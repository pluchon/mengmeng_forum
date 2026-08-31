package org.pluchon.forum.service.impl.game;

// 俄罗斯方块业务常量
public class TetrisConstants {

    public static final String GAME_CODE = "tetris";
    public static final String VALIDATION_VALID = "VALID";
    // 重放算出的分数与自报一致
    public static final String VALIDATION_REPLAY_OK = "REPLAY_OK";
    // 重放算出的分数与自报不一致。灰度期只记录不拒绝
    public static final String VALIDATION_MISMATCH = "MISMATCH";
    // 旧格式、解析失败、超时等无法校验的情况
    public static final String VALIDATION_SKIPPED = "SKIPPED";

    public static final int SETTLE_RATE_LIMIT_PER_HOUR = 40;
    public static final long SETTLE_RATE_LIMIT_TTL_SECONDS = 3_600L;

    private TetrisConstants() {
    }
}
