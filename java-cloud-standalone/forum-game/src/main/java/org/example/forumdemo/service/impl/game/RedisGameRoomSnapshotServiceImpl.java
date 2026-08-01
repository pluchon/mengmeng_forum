package org.example.forumdemo.service.impl.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.GameRedisKeys;
import org.example.forumdemo.entity.vo.game.GameRoomSnapshotVO;
import org.example.forumdemo.service.interfaces.game.GameRoomSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Redis 房间快照服务，只保存实时恢复所需的轻量状态，不参与最终结算判断
@Slf4j
@Service
public class RedisGameRoomSnapshotServiceImpl implements GameRoomSnapshotService {

    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(30);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void saveSnapshot(GameRoomSnapshotVO snapshot) {
        if (snapshot == null || snapshot.getGameCode() == null || snapshot.getRoomId() == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    GameRedisKeys.roomState(snapshot.getGameCode(), snapshot.getRoomId()),
                    objectMapper.writeValueAsString(snapshot),
                    SNAPSHOT_TTL
            );
        } catch (Exception e) {
            log.debug("保存游戏房间 Redis 快照失败 gameCode={}, roomId={}, error={}",
                    snapshot.getGameCode(),
                    snapshot.getRoomId(),
                    e.getMessage());
        }
    }

    @Override
    public GameRoomSnapshotVO getSnapshot(String gameCode, String roomId) {
        if (gameCode == null || gameCode.isBlank() || roomId == null || roomId.isBlank()) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(GameRedisKeys.roomState(gameCode, roomId));
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, GameRoomSnapshotVO.class);
        } catch (Exception e) {
            log.debug("读取游戏房间 Redis 快照失败 gameCode={}, roomId={}, error={}",
                    gameCode,
                    roomId,
                    e.getMessage());
            return null;
        }
    }
}
