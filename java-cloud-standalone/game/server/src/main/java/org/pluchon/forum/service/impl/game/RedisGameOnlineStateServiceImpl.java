package org.pluchon.forum.service.impl.game;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.GameRedisKeys;
import org.pluchon.forum.service.interfaces.game.GameOnlineStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

// Redis 游戏在线状态服务，区分大厅在线和具体游戏在线，Redis 异常时由调用方回退内存统计
@Slf4j
@Service
public class RedisGameOnlineStateServiceImpl implements GameOnlineStateService {

    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(90);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void enterLobby(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForSet().add(GameRedisKeys.lobbyOnline(), String.valueOf(userId));
            touchLobby(userId);
        } catch (Exception e) {
            log.debug("写入游戏大厅在线状态失败 userId={}, error={}", userId, e.getMessage());
        }
    }

    @Override
    public void leaveLobby(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForSet().remove(GameRedisKeys.lobbyOnline(), String.valueOf(userId));
            stringRedisTemplate.delete(GameRedisKeys.lobbyHeartbeat(userId));
        } catch (Exception e) {
            log.debug("清理游戏大厅在线状态失败 userId={}, error={}", userId, e.getMessage());
        }
    }

    @Override
    public void touchLobby(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForSet().add(GameRedisKeys.lobbyOnline(), String.valueOf(userId));
            stringRedisTemplate.opsForValue().set(GameRedisKeys.lobbyHeartbeat(userId), "1", HEARTBEAT_TTL);
        } catch (Exception e) {
            log.debug("刷新游戏大厅心跳失败 userId={}, error={}", userId, e.getMessage());
        }
    }

    @Override
    public int countLobbyOnline() {
        return countOnline(GameRedisKeys.lobbyOnline(), null);
    }

    @Override
    public void enterGame(String gameCode, Long userId) {
        if (gameCode == null || gameCode.isBlank() || userId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForSet().add(GameRedisKeys.gameOnline(gameCode), String.valueOf(userId));
            touchGame(gameCode, userId);
        } catch (Exception e) {
            log.debug("写入游戏在线状态失败 gameCode={}, userId={}, error={}", gameCode, userId, e.getMessage());
        }
    }

    @Override
    public void leaveGame(String gameCode, Long userId) {
        if (gameCode == null || gameCode.isBlank() || userId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForSet().remove(GameRedisKeys.gameOnline(gameCode), String.valueOf(userId));
            stringRedisTemplate.delete(GameRedisKeys.gameHeartbeat(gameCode, userId));
        } catch (Exception e) {
            log.debug("清理游戏在线状态失败 gameCode={}, userId={}, error={}", gameCode, userId, e.getMessage());
        }
    }

    @Override
    public void touchGame(String gameCode, Long userId) {
        if (gameCode == null || gameCode.isBlank() || userId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForSet().add(GameRedisKeys.gameOnline(gameCode), String.valueOf(userId));
            stringRedisTemplate.opsForValue().set(GameRedisKeys.gameHeartbeat(gameCode, userId), "1", HEARTBEAT_TTL);
        } catch (Exception e) {
            log.debug("刷新游戏心跳失败 gameCode={}, userId={}, error={}", gameCode, userId, e.getMessage());
        }
    }

    @Override
    public int countGameOnline(String gameCode) {
        if (gameCode == null || gameCode.isBlank()) {
            return 0;
        }
        return countOnline(GameRedisKeys.gameOnline(gameCode), gameCode);
    }

    private int countOnline(String setKey, String gameCode) {
        try {
            Set<String> userIds = stringRedisTemplate.opsForSet().members(setKey);
            if (userIds == null || userIds.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (String userId : userIds) {
                String heartbeatKey = gameCode == null
                        ? GameRedisKeys.lobbyHeartbeat(Long.valueOf(userId))
                        : GameRedisKeys.gameHeartbeat(gameCode, Long.valueOf(userId));
                Boolean alive = stringRedisTemplate.hasKey(heartbeatKey);
                if (Boolean.TRUE.equals(alive)) {
                    count++;
                } else {
                    stringRedisTemplate.opsForSet().remove(setKey, userId);
                }
            }
            return count;
        } catch (Exception e) {
            log.debug("读取游戏在线人数失败 setKey={}, error={}", setKey, e.getMessage());
            return -1;
        }
    }
}
