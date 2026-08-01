package org.example.forumdemo.service.impl.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.service.interfaces.game.GameRoomStateCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

// 游戏房间状态缓存，用于非房主实例兜底返回房间初始状态
@Slf4j
@Service
public class RedisGameRoomStateCacheServiceImpl implements GameRoomStateCacheService {

    private static final Duration STATE_TTL = Duration.ofHours(2);

    private static final String STATE_KEY_PREFIX = "forum:game:room:state:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void saveState(String gameCode, String roomId, Long userId, Object state) {
        if (gameCode == null || gameCode.isBlank()
                || roomId == null || roomId.isBlank()
                || userId == null
                || state == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    key(gameCode, roomId, userId),
                    objectMapper.writeValueAsString(state),
                    STATE_TTL
            );
        } catch (Exception e) {
            log.debug("缓存游戏房间状态失败 gameCode={}, roomId={}, userId={}, error={}",
                    gameCode,
                    roomId,
                    userId,
                    e.getMessage());
        }
    }

    @Override
    public <T> T getState(String gameCode, String roomId, Long userId, Class<T> stateType) {
        if (gameCode == null || gameCode.isBlank()
                || roomId == null || roomId.isBlank()
                || userId == null
                || stateType == null) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(key(gameCode, roomId, userId));
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, stateType);
        } catch (Exception e) {
            log.debug("读取游戏房间状态缓存失败 gameCode={}, roomId={}, userId={}, error={}",
                    gameCode,
                    roomId,
                    userId,
                    e.getMessage());
            return null;
        }
    }

    private String key(String gameCode, String roomId, Long userId) {
        return STATE_KEY_PREFIX + gameCode + ":" + roomId + ":" + userId;
    }
}
