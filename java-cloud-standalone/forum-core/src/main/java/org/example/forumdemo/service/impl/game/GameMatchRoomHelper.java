package org.example.forumdemo.service.impl.game;

import org.example.forumdemo.common.constant.ForumRedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** 匹配建房幂等：同一对用户并发匹配只保留一个房间 */
@Component
public class GameMatchRoomHelper {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public String resolveMatchedRoomId(String gameCode, Long userIdA, Long userIdB,
                                       Supplier<String> roomCreator, Predicate<String> roomExists) {
        long minUser = Math.min(userIdA, userIdB);
        long maxUser = Math.max(userIdA, userIdB);
        String matchKey = ForumRedisKeys.GAME_MATCH_ROOM + gameCode + ":" + minUser + ":" + maxUser;
        String existingRoomId = stringRedisTemplate.opsForValue().get(matchKey);
        if (existingRoomId != null && roomExists.test(existingRoomId)) {
            return existingRoomId;
        }
        String roomId = roomCreator.get();
        stringRedisTemplate.opsForValue().set(matchKey, roomId, 2, TimeUnit.HOURS);
        return roomId;
    }
}
