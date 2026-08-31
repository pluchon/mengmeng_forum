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
            stringRedisTemplate.opsForZSet().remove(GameRedisKeys.lobbyOnline(), String.valueOf(userId));
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
            stringRedisTemplate.opsForZSet().add(GameRedisKeys.lobbyOnline(),
                    String.valueOf(userId), System.currentTimeMillis());
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
            stringRedisTemplate.opsForZSet().remove(GameRedisKeys.gameOnline(gameCode), String.valueOf(userId));
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
            stringRedisTemplate.opsForZSet().add(GameRedisKeys.gameOnline(gameCode),
                    String.valueOf(userId), System.currentTimeMillis());
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

    /**
     * 用 ZSet 的 score 存最后心跳时间戳。
     *
     * <p>原来是 Set 存 userId + 每人一个带 TTL 的心跳 key，计数时遍历 Set 逐个
     * hasKey——在线 N 人就是 N 次往返，而首页会为每个游戏各调一次、每 5 秒一轮。
     * 而且 Set 只有被扫到才清理，长期没人看的游戏会一直堆历史 ID。
     * 现在固定两次往返，与在线人数无关。
     */
    private int countOnline(String setKey, String gameCode) {
        try {
            long now = System.currentTimeMillis();
            stringRedisTemplate.opsForZSet().removeRangeByScore(setKey, 0, now - HEARTBEAT_TTL.toMillis());
            Long size = stringRedisTemplate.opsForZSet().zCard(setKey);
            return size == null ? 0 : size.intValue();
        } catch (Exception e) {
            log.debug("读取游戏在线人数失败 setKey={}, error={}", setKey, e.getMessage());
            return -1;
        }
    }
}
