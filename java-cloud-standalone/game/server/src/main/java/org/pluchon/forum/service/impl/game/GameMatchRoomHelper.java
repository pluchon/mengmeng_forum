package org.pluchon.forum.service.impl.game;

import org.pluchon.forum.common.constant.ForumRedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

// 匹配建房幂等：同一对用户并发匹配只保留一个房间
@Component
public class GameMatchRoomHelper {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public String resolveMatchedRoomId(String gameCode, Long userIdA, Long userIdB,
                                       Supplier<String> roomCreator, Predicate<String> roomExists) {
        String matchKey = matchKey(gameCode, userIdA, userIdB);
        String existingRoomId = stringRedisTemplate.opsForValue().get(matchKey);
        if (existingRoomId != null && roomExists.test(existingRoomId)) {
            return existingRoomId;
        }
        String roomId = roomCreator.get();
        stringRedisTemplate.opsForValue().set(matchKey, roomId, 2, TimeUnit.HOURS);
        return roomId;
    }

    /**
     * 对局结束后释放这一对用户的建房记录。
     *
     * <p>不释放的话这条 key 还要在 Redis 里躺两小时，同一对人再匹配就会命中它。
     * 虽然还有一层「房间是否存在」的判断兜底，但那道判断只看当前实例的内存，
     * 房间散场后本就不该再让任何人拿到这个房号。
     */
    public void releaseMatchedRoom(String gameCode, Long userIdA, Long userIdB) {
        if (gameCode == null || gameCode.isBlank() || userIdA == null || userIdB == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(matchKey(gameCode, userIdA, userIdB));
        } catch (Exception ignored) {
            // 释放失败最多让下一次匹配多走一次「房间已不存在」的判断，不必打断结算
        }
    }

    private String matchKey(String gameCode, Long userIdA, Long userIdB) {
        long minUser = Math.min(userIdA, userIdB);
        long maxUser = Math.max(userIdA, userIdB);
        return ForumRedisKeys.GAME_MATCH_ROOM + gameCode + ":" + minUser + ":" + maxUser;
    }
}
