package org.pluchon.forum.service.impl.game;

// 俄罗斯方块业务常量
public class TetrisConstants {

    public static final String GAME_CODE = "tetris";
    public static final String VALIDATION_VALID = "VALID";

    public static final int SETTLE_RATE_LIMIT_PER_HOUR = 40;
    public static final long SETTLE_RATE_LIMIT_TTL_SECONDS = 3_600L;

    private TetrisConstants() {
    }
}
