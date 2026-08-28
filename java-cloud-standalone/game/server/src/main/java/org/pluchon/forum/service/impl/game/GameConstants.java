package org.pluchon.forum.service.impl.game;

// 游戏模块稳定常量，集中维护状态编码和消息类型
public class GameConstants {

    
    public static final byte NOT_DELETED = 0;

    
    public static final String GOBANG = "gobang";
    public static final String JINZI = "jinzi";
    public static final String TETRIS = "tetris";
    public static final String TETRIS_PK = "tetris_pk";

    
    public static final String PROFILE_IDLE = "IDLE";
    public static final String PROFILE_MATCHING = "MATCHING";
    public static final String PROFILE_PLAYING = "PLAYING";

    
    public static final String ROOM_WAITING = "WAITING";
    public static final String ROOM_PLAYING = "PLAYING";
    public static final String ROOM_FINISHED = "FINISHED";

    
    public static final String END_FIVE = "FIVE";
    public static final String END_LINE = "LINE";
    public static final String END_DRAW = "DRAW";
    public static final String END_SURRENDER = "SURRENDER";
    public static final String END_DISCONNECT = "DISCONNECT";
    public static final String END_TIMEOUT = "TIMEOUT";

    
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
    public static final int BOARD_SIZE = 15;
    public static final long GAME_TIME_MS = 10 * 60 * 1000L;
    public static final long MOVE_TIME_MS = 60 * 1000L;
    public static final long AI_MATCH_TIMEOUT_MS = 28 * 1000L;
    public static final Long AI_USER_ID = -1L;

    
    public static final int JINZI_BOARD_SIZE = 3;
    public static final long JINZI_GAME_TIME_MS = 2 * 60 * 1000L;
    public static final long JINZI_MOVE_TIME_MS = 20 * 1000L;
    public static final long JINZI_RECONNECT_WINDOW_MS = 30 * 1000L;
    public static final long GOBANG_RECONNECT_WINDOW_MS = 60 * 1000L;
    public static final long TETRIS_RECONNECT_WINDOW_MS = 60 * 1000L;
    private GameConstants() {
    }
}
