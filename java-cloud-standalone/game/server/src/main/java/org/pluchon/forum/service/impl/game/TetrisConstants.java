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

    /**
     * 计入个人记录与对局统计的校验状态。
     *
     * <p>重放校验上线后 settle 不再写 VALID，只写 REPLAY_OK / SKIPPED / MISMATCH，
     * 而查询侧还只认 VALID——新成绩会从记录列表和统计里凭空消失。
     *
     * <p>MISMATCH 也在内：灰度期的承诺是「只记录不拒绝」，把它排除出统计就等于拒绝了，
     * 而且服务端引擎万一与前端有细微差异，受害的是真实成绩。等观察到零误判、
     * 正式改为拒绝时，再把它从这里拿掉。
     */
    public static final java.util.List<String> COUNTED_VALIDATION_STATUSES = java.util.List.of(
            VALIDATION_VALID,
            VALIDATION_REPLAY_OK,
            VALIDATION_SKIPPED,
            VALIDATION_MISMATCH);

    public static final int SETTLE_RATE_LIMIT_PER_HOUR = 40;
    public static final long SETTLE_RATE_LIMIT_TTL_SECONDS = 3_600L;

    private TetrisConstants() {
    }
}
