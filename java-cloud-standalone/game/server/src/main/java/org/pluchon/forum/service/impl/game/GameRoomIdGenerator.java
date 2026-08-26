package org.pluchon.forum.service.impl.game;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

// 游戏房间 6 位纯数字短编号生成工具
public final class GameRoomIdGenerator {

    private static final int MIN_ROOM_ID = 100000;
    private static final int MAX_ROOM_ID = 999999;

    private GameRoomIdGenerator() {
    }

    public static String generateRoomId() {
        return generateRoomId(null);
    }

    public static String generateRoomId(Predicate<String> existsChecker) {
        for (int i = 0; i < 20; i++) {
            int num = ThreadLocalRandom.current().nextInt(MIN_ROOM_ID, MAX_ROOM_ID + 1);
            String id = String.valueOf(num);
            if (existsChecker == null || !existsChecker.test(id)) {
                return id;
            }
        }
        return String.valueOf(ThreadLocalRandom.current().nextInt(MIN_ROOM_ID, MAX_ROOM_ID + 1));
    }
}
