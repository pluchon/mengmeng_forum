package org.example.forumdemo.service.impl.game;

// 游戏模块稳定常量，集中维护状态编码和消息类型
public class GameConstants {

    public static final String GOBANG = "gobang";

    public static final String PROFILE_IDLE = "IDLE";
    public static final String PROFILE_MATCHING = "MATCHING";
    public static final String PROFILE_PLAYING = "PLAYING";

    public static final String ROOM_WAITING = "WAITING";
    public static final String ROOM_PLAYING = "PLAYING";
    public static final String ROOM_FINISHED = "FINISHED";

    public static final String END_FIVE = "FIVE";
    public static final String END_SURRENDER = "SURRENDER";
    public static final String END_DISCONNECT = "DISCONNECT";
    public static final String END_TIMEOUT = "TIMEOUT";
    public static final String END_ABNORMAL = "ABNORMAL";

    public static final String SETTLEMENT_EVENT_GAME_FINISHED = "GAME_FINISHED";
    public static final String SETTLEMENT_EVENT_CREATED = "CREATED";
    public static final String SETTLEMENT_EVENT_MQ_SENT = "MQ_SENT";
    public static final String SETTLEMENT_EVENT_MQ_PENDING = "MQ_PENDING";
    public static final String SETTLEMENT_EVENT_CONSUMED = "CONSUMED";
    public static final String SETTLEMENT_EVENT_DEAD = "DEAD";

    public static final String MATCH_BUCKET_BRONZE = "bronze";
    public static final String MATCH_BUCKET_SILVER = "silver";
    public static final String MATCH_BUCKET_GOLD = "gold";
    public static final String MATCH_BUCKET_MASTER = "master";

    public static final int INITIAL_SCORE = 1000;
    public static final int SCORE_DELTA = 10;
    public static final int BOARD_SIZE = 15;
    public static final long GAME_TIME_MS = 10 * 60 * 1000L;
    public static final long MOVE_TIME_MS = 60 * 1000L;
    public static final long AI_MATCH_TIMEOUT_MS = 15 * 1000L;
    public static final Long AI_USER_ID = -1L;

    private GameConstants() {
    }
}
