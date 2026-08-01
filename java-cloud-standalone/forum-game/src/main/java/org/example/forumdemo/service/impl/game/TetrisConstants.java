package org.example.forumdemo.service.impl.game;

// 俄罗斯方块业务常量
public class TetrisConstants {

    public static final String GAME_CODE = "tetris";
    public static final String VALIDATION_VALID = "VALID";

    public static final int TIER_QUALIFIED_SCORE = 500;
    public static final int TIER_GOOD_SCORE = 2_000;
    public static final int TIER_HIGH_SCORE = 5_000;

    public static final int SETTLE_RATE_LIMIT_PER_HOUR = 40;
    public static final long SETTLE_RATE_LIMIT_TTL_SECONDS = 3_600L;

    private TetrisConstants() {
    }
}
