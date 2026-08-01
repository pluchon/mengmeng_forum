package org.example.forumdemo.common.constant;

// 游戏模块 Redis Key 生成器，集中维护在线态、心跳和后续匹配房间状态前缀
public class GameRedisKeys {

    private static final String PREFIX = "forum:game:";

    private GameRedisKeys() {
    }

    public static String lobbyOnline() {
        return PREFIX + "lobby:online";
    }

    public static String lobbyHeartbeat(Long userId) {
        return PREFIX + "lobby:heartbeat:" + userId;
    }

    public static String gameOnline(String gameCode) {
        return PREFIX + gameCode + ":online";
    }

    public static String gameHeartbeat(String gameCode, Long userId) {
        return PREFIX + gameCode + ":heartbeat:" + userId;
    }

    public static String matchQueue(String gameCode, String bucket) {
        return PREFIX + gameCode + ":match:" + bucket;
    }

    public static String matchUser(String gameCode, Long userId) {
        return PREFIX + gameCode + ":match:user:" + userId;
    }

    public static String matchUserPrefix(String gameCode) {
        return PREFIX + gameCode + ":match:user:";
    }

    public static String matchQueuePattern(String gameCode) {
        return PREFIX + gameCode + ":match:*";
    }

    public static String roomState(String gameCode, String roomId) {
        return PREFIX + gameCode + ":room:" + roomId + ":state";
    }

    public static String tetrisSettleRate(Long userId) {
        return PREFIX + "tetris:settle:rate:" + userId;
    }
}
