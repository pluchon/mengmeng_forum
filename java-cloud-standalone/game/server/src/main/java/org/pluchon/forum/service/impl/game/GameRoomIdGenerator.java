package org.pluchon.forum.service.impl.game;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

// 游戏房间 6 位纯数字短编号生成工具
public final class GameRoomIdGenerator {

    private static final int MIN_ROOM_ID = 100000;
    private static final int MAX_ROOM_ID = 999999;
    private static final int MAX_ATTEMPTS = 30;
    private static final java.util.regex.Pattern ROOM_ID_PATTERN =
            java.util.regex.Pattern.compile("^[1-9]\\d{5}$");

    private GameRoomIdGenerator() {
    }

    public static String generateRoomId() {
        return generateRoomId(null);
    }

    /**
     * 生成 6 位房间号。
     *
     * <p>务必传入 existsChecker：房间按 roomId 存在 Map 里，撞号会让后建的房间
     * 把先建的挤掉，或者观战时进错房。90 万空间看着大，但按生日问题，同时在线
     * 1000 个房间时出现碰撞的概率已经过半。
     */
    public static String generateRoomId(Predicate<String> existsChecker) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            int num = ThreadLocalRandom.current().nextInt(MIN_ROOM_ID, MAX_ROOM_ID + 1);
            String id = String.valueOf(num);
            if (existsChecker == null || !existsChecker.test(id)) {
                return id;
            }
        }
        // 重试耗尽说明房间号空间已经很满，静默返回一个可能碰撞的号只会把问题藏起来
        throw new IllegalStateException("生成房间号失败：重试 " + MAX_ATTEMPTS + " 次仍然碰撞");
    }

    /** 房间号格式：6 位纯数字。用于校验外部传入的房间号 */
    public static boolean isValidRoomId(String roomId) {
        return roomId != null && ROOM_ID_PATTERN.matcher(roomId).matches();
    }
}
