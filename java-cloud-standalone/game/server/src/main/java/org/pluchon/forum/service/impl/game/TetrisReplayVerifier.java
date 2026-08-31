package org.pluchon.forum.service.impl.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.service.impl.game.tetris.TetrisBlock;
import org.pluchon.forum.service.impl.game.tetris.TetrisEngineConstants;
import org.pluchon.forum.service.impl.game.tetris.TetrisMatrixUtil;
import org.pluchon.forum.service.impl.game.tetris.TetrisRng;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 俄罗斯方块成绩重放校验。
 *
 * <p><b>为什么按落子序列而不是按键流：</b>原来录制的是 {@code {t, a}} 按键流，
 * 但重力下落不产生任何输入事件——玩家不按加速让方块自然落下时，这个方块的整个
 * 生命周期在录像里没有痕迹。服务端只能自己模拟重力，而暂停、定时器漂移、后台标签页
 * 降频这三件事会让模拟与客户端必然发散，正常玩家会被误判。
 *
 * <p>改录落子结果后，验证与时间完全无关：给定 seed，方块序列是确定的，只需核对
 * 每一子的类型对不对、落点是否合法，再按同一套计分规则累加。玩家暂停多久、卡顿多少
 * 都不影响结果。
 *
 * <p><b>灰度期的姿态：</b>这个类只回答「重放算出多少分」，任何异常一律返回
 * {@link Result#skipped}，由调用方决定是否拒绝。上线初期只记录不拒绝——服务端引擎
 * 万一与前端有细微差异，宁可漏过作弊也不能把真实成绩判死。
 */
@Slf4j
@Component
public class TetrisReplayVerifier {

    /** 支持重放校验的录像格式版本；低于此版本的旧记录跳过 */
    public static final int REPLAY_VERSION_LOCKS = 3;

    /** 一局最多重放多少个方块，防止构造超长序列拖垮服务端 */
    private static final int MAX_LOCKS = 5_000;

    /** 重放耗时上限，超过就放弃校验而不是拒绝成绩 */
    private static final long MAX_REPLAY_MS = 500L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 重放结论。score 仅在 verified 为 true 时有意义 */
    public record Result(boolean verified, int score, int lines, String reason) {

        public static Result skipped(String reason) {
            return new Result(false, 0, 0, reason);
        }

        public static Result of(int score, int lines) {
            return new Result(true, score, lines, null);
        }
    }

    public Result verify(String replayPayload) {
        try {
            return doVerify(replayPayload);
        } catch (Exception e) {
            // 校验器自身出问题绝不能影响结算：宁可不校验
            log.debug("俄罗斯方块重放校验异常: {}", e.getMessage());
            return Result.skipped("ERROR");
        }
    }

    /**
     * 还原一个落子。
     *
     * <p>旋转态只能靠 {@link TetrisBlock#rotate()} 一步步转出来——直接构造会绕过
     * 各方块自己的踢墙偏移表，转出客户端不可能达到的形态。
     */
    private TetrisBlock buildBlock(String type, int x, int y, int rotation) {
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || rotation < 0 || rotation > 3) {
            return null;
        }
        TetrisBlock block = TetrisBlock.spawn(type);
        if (block == null) {
            return null;
        }
        for (int i = 0; i < rotation; i++) {
            block = block.rotate();
        }
        return new TetrisBlock(block.getType(), block.getShape(), new int[]{y, x}, rotation);
    }

    private Result doVerify(String replayPayload) throws Exception {
        if (replayPayload == null || replayPayload.isBlank()) {
            return Result.skipped("EMPTY");
        }
        JsonNode root = objectMapper.readTree(replayPayload);
        JsonNode versionNode = root.get("v");
        int version = versionNode == null || !versionNode.isNumber() ? 0 : versionNode.intValue();
        if (version < REPLAY_VERSION_LOCKS) {
            // 旧格式录的是按键流，没有落子序列，无法脱离时间重建
            return Result.skipped("LEGACY_FORMAT");
        }
        JsonNode seedNode = root.get("seed");
        if (seedNode == null || !seedNode.isNumber()) {
            return Result.skipped("NO_SEED");
        }
        JsonNode locks = root.get("locks");
        if (locks == null || !locks.isArray()) {
            return Result.skipped("NO_LOCKS");
        }
        if (locks.size() > MAX_LOCKS) {
            return Result.skipped("TOO_MANY_LOCKS");
        }

        long deadline = System.currentTimeMillis() + MAX_REPLAY_MS;
        TetrisRng rng = TetrisRng.create(seedNode.longValue());
        String[][] matrix = TetrisEngineConstants.createBlankMatrix();
        // 方块序列必须与前端 useTetrisEngine 完全一致，否则用过 hold 的对局全会误报。
        // 前端 spawnNext：current 取 next，然后 next 重新抽一个
        String next = rng.pickBlockType();
        String current = next;
        next = rng.pickBlockType();
        String held = null;
        int score = 0;
        int lines = 0;
        int combo = 0;
        int level = 1;

        for (JsonNode lock : locks) {
            if (System.currentTimeMillis() > deadline) {
                return Result.skipped("TIMEOUT");
            }
            // 前端 holdPiece：有存货就与当前互换，没有则当前换成 next 并重抽 next
            if (lock.path("h").asBoolean(false)) {
                String previous = current;
                if (held != null) {
                    current = held;
                } else {
                    current = next;
                    next = rng.pickBlockType();
                }
                held = previous;
            }
            String reported = lock.path("t").asText(null);
            if (reported == null || !reported.equals(current)) {
                // 方块类型对不上，说明序列被伪造过
                return Result.skipped("TYPE_MISMATCH");
            }
            String type = current;
            int x = lock.path("x").asInt(Integer.MIN_VALUE);
            int y = lock.path("y").asInt(Integer.MIN_VALUE);
            int rotation = lock.path("r").asInt(0);
            TetrisBlock block = buildBlock(type, x, y, rotation);
            if (block == null || !TetrisMatrixUtil.canPlace(block, matrix)) {
                return Result.skipped("ILLEGAL_PLACEMENT");
            }
            // 落子必须贴底：客户端落地即锁、没有锁定延迟，所以合法落点一定不能再下落一格
            if (TetrisMatrixUtil.canPlace(block.fall(1), matrix)) {
                return Result.skipped("NOT_RESTING");
            }

            matrix = TetrisMatrixUtil.mergeBlock(matrix, block);
            score += 10 + (level - 1) * 2;
            List<Integer> full = TetrisMatrixUtil.findClearLines(matrix);
            if (full != null && !full.isEmpty()) {
                matrix = TetrisMatrixUtil.clearLineRows(matrix, full);
                lines += full.size();
                combo += 1;
                score += TetrisEngineConstants.calcClearScore(full.size(), combo);
                level = Math.min(6, 1 + lines / TetrisEngineConstants.EACH_LINES);
            } else {
                combo = 0;
            }
            // 落子后 spawnNext：current 取 next，next 重新抽
            current = next;
            next = rng.pickBlockType();
        }
        return Result.of(score, lines);
    }
}
