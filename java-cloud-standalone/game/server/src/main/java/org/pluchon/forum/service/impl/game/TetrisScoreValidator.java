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
        validateReplayPayload(request);
        validateScoreBounds(request);
    }

    private void validateReplayPayload(TetrisSettleRequest request) {
        try {
            JsonNode root = objectMapper.readTree(request.getReplayPayload());
            JsonNode seedNode = root.get("seed");
            if (seedNode == null || !seedNode.isNumber() || seedNode.longValue() != request.getSeed()) {
                throw invalid();
            }
            JsonNode inputs = root.get("inputs");
            if (inputs != null && !inputs.isArray()) {
                throw invalid();
            }
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw invalid();
        }
    }

    private void validateScoreBounds(TetrisSettleRequest request) {
        int score = request.getScore();
        int lines = request.getLinesCleared();
        long duration = request.getDurationMs();
        if (lines == 0) {
            long zeroLineUpper = duration / 400L * 15L + 2000;
            if (score > zeroLineUpper) {
                throw invalid();
            }
            return;
        }
        if (score < lines * 50) {
            throw invalid();
        }
        long pieceBudget = duration / 300L;
        // 四消 1500 + 连击 20% + 固定 200 ≈ 2000/次；留 piece 落锁分余量
        int upper = lines * 2000 + (int) (pieceBudget * 15L) + 5000;
        if (score > upper) {
            throw invalid();
        }
    }

    public int resolveForumPoints(int score) {
        if (score >= TetrisConstants.TIER_HIGH_SCORE) {
            return 3;
        }
        if (score >= TetrisConstants.TIER_GOOD_SCORE) {
            return 2;
        }
        if (score >= TetrisConstants.TIER_QUALIFIED_SCORE) {
            return 1;
        }
        return 0;
    }

    private ApplicationException invalid() {
        return new ApplicationException(Result.fail(ResultCode.FAILED_TETRIS_SETTLE_INVALID));
    }
}
