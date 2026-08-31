package org.pluchon.forum.service.impl.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.dto.game.TetrisSettleRequest;
import org.springframework.stereotype.Component;

// 俄罗斯方块成绩合理性校验，一期不做完整 replay 重放
@Component
public class TetrisScoreValidator {

    private static final long MIN_DURATION_MS = 1_000L;
    private static final long MAX_DURATION_MS = 3_600_000L;
    private static final int MAX_LINES = 200;
    private static final int MAX_LEVEL = 6;

    // 一局最多录多少条操作。人类一小时按每秒 5 次连打也就 18000 条
    private static final int MAX_INPUTS = 20_000;
    // 客户端与服务端的时钟容差
    private static final long CLOCK_SKEW_TOLERANCE_MS = 5 * 60_000L;
    // 四消带满连击 2000 分 / 4 行
    private static final int MAX_POINTS_PER_LINE = 500;
    // 单行最低 100 分；四消 2000 摊到每行 500，取保守下界
    private static final int MIN_POINTS_PER_LINE = 50;
    private static final int CELLS_PER_LINE = 10;
    private static final int CELLS_PER_PIECE = 4;
    // 20×10 的棋盘最多能塞下的方块数
    private static final int BOARD_PIECE_CAPACITY = 50;
    // 6 级时的落锁分 10 + (6-1)*2
    private static final int MAX_LOCK_POINTS = 20;
    private static final int MIN_MS_PER_PIECE = 120;
    private static final int SCORE_MARGIN = 500;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void validate(TetrisSettleRequest request) {
        if (request == null) {
            throw invalid();
        }
        if (request.getSeed() == null || request.getSeed() <= 0) {
            throw invalid();
        }
        if (request.getScore() == null || request.getScore() < 0) {
            throw invalid();
        }
        if (request.getLevel() == null || request.getLevel() < 1 || request.getLevel() > MAX_LEVEL) {
            throw invalid();
        }
        if (request.getLinesCleared() == null
                || request.getLinesCleared() < 0
                || request.getLinesCleared() > MAX_LINES) {
            throw invalid();
        }
        if (request.getDurationMs() == null
                || request.getDurationMs() < MIN_DURATION_MS
                || request.getDurationMs() > MAX_DURATION_MS) {
            throw invalid();
        }
        if (request.getReplayPayload() == null || request.getReplayPayload().isBlank()) {
            throw invalid();
        }
        validateStartedAt(request);
        validateReplayPayload(request);
        validateScoreBounds(request);
    }

    /**
     * 开局时间由客户端上报，原来直接落库不做任何校验。
     *
     * <p>可以造出「结束早于开始」或开始时间在未来的记录，按时间排序的统计与回放列表会错位。
     */
    private void validateStartedAt(TetrisSettleRequest request) {
        Long startedAtMs = request.getStartedAtMs();
        if (startedAtMs == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (startedAtMs > now + CLOCK_SKEW_TOLERANCE_MS) {
            throw invalid();
        }
        long impliedEnd = startedAtMs + request.getDurationMs();
        // 客户端与服务端时钟本来就有偏差，只挡明显不自洽的，不做精确比对
        if (Math.abs(now - impliedEnd) > CLOCK_SKEW_TOLERANCE_MS) {
            throw invalid();
        }
    }

    private void validateReplayPayload(TetrisSettleRequest request) {
        try {
            JsonNode root = objectMapper.readTree(request.getReplayPayload());
            JsonNode seedNode = root.get("seed");
            if (seedNode == null || !seedNode.isNumber() || seedNode.longValue() != request.getSeed()) {
                throw invalid();
            }
            JsonNode inputs = root.get("inputs");
            if (inputs != null) {
                if (!inputs.isArray()) {
                    throw invalid();
                }
                // 原来只校验「是不是数组」，超大 JSON 会被原样存进 replay_payload
                if (inputs.size() > MAX_INPUTS) {
                    throw invalid();
                }
            }
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw invalid();
        }
    }

    /**
     * 分数上界。
     *
     * <p>原来是 {@code lines * 2000 + duration/300 * 15 + 5000}：一小时 200 行可以合法
     * 自报 58.5 万。两处都太松——{@code lines * 2000} 把「四消一次得 2000」错当成
     * 「每行得 2000」，而 duration 项默认玩家能在一小时里锁 12000 个方块，
     * 但棋盘只有 20×10，放不下这么多不消行的方块。
     *
     * <p>改从游戏规则推：单行最高得分是四消带满连击的 2000/4 = 500；落锁分最高
     * 每个 20（6 级）；而方块总数受棋盘容量约束——消掉 n 行至少要 n*10/4 个方块，
     * 加上最后残留在盘面上的至多 20*10/4 = 50 个。
     */
    private void validateScoreBounds(TetrisSettleRequest request) {
        int score = request.getScore();
        int lines = request.getLinesCleared();
        long duration = request.getDurationMs();

        // 消行得分上界：每行最多 500（全部四消且连击拉满）
        long clearUpper = (long) lines * MAX_POINTS_PER_LINE;
        // 方块数上界：消行消耗的 + 棋盘最多残留的
        long pieceUpper = (long) Math.ceil(lines * CELLS_PER_LINE / (double) CELLS_PER_PIECE) + BOARD_PIECE_CAPACITY;
        // 手速上界：再快也要一段时间落一个方块，短局不该按棋盘容量放行
        pieceUpper = Math.min(pieceUpper, duration / MIN_MS_PER_PIECE + 1);
        long lockUpper = pieceUpper * MAX_LOCK_POINTS;

        long upper = clearUpper + lockUpper + SCORE_MARGIN;
        if (score > upper) {
            throw invalid();
        }
        // 下界：消了 n 行至少拿到 n 行的基础分（单行 100，四消摊到每行 375）
        if (lines > 0 && score < (long) lines * MIN_POINTS_PER_LINE) {
            throw invalid();
        }
    }

    private ApplicationException invalid() {
        return new ApplicationException(Result.fail(ResultCode.FAILED_TETRIS_SETTLE_INVALID));
    }
}
