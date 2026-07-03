package org.example.forumdemo.service.impl.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.websocket.game.GameConnectionRegistry;
import org.example.forumdemo.service.interfaces.game.GameRoomEventBusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

// Redis 房间事件总线，用于多实例下把房间事件转发给持有本地 WebSocket 的节点
@Slf4j
@Service
public class RedisGameRoomEventBusServiceImpl implements GameRoomEventBusService {

    private static final String TARGET_ROOM = "ROOM";

    private static final String TARGET_ROOM_USER = "ROOM_USER";

    private static final String TARGET_GAME = "GAME";

    private static final String TARGET_COMMAND = "ROOM_COMMAND";

    private final String instanceId = UUID.randomUUID().toString();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @PostConstruct
    public void subscribe() {
        MessageListener listener = (message, pattern) -> {
            try {
                String body = new String(message.getBody());
                @SuppressWarnings("unchecked")
                Map<String, Object> envelope = objectMapper.readValue(body, Map.class);
                Object sourceObj = envelope.get("sourceInstanceId");
                if (instanceId.equals(String.valueOf(sourceObj))) {
                    return;
                }
                Object payloadObj = envelope.get("payload");
                if (payloadObj == null) {
                    return;
                }
                String payload = payloadObj instanceof String s
                        ? s
                        : objectMapper.writeValueAsString(payloadObj);
                String targetType = String.valueOf(envelope.getOrDefault("targetType", TARGET_ROOM));
                if (TARGET_GAME.equals(targetType)) {
                    deliverGameEvent(envelope, payload);
                    return;
                }
                if (TARGET_ROOM.equals(targetType)) {
                    deliverRoomEvent(envelope, payload);
                    return;
                }
                if (TARGET_ROOM_USER.equals(targetType)) {
                    deliverRoomUserEvent(envelope, payload);
                }
            } catch (Exception e) {
                log.debug("游戏房间 Redis 事件处理失败: {}", e.getMessage());
            }
        };
        redisMessageListenerContainer.addMessageListener(listener, new ChannelTopic(Constant.GAME_ROOM_EVENT_CHANNEL));
    }

    private void deliverRoomEvent(Map<String, Object> envelope, String payload) {
        Object roomIdObj = envelope.get("roomId");
        if (roomIdObj == null) {
            return;
        }
        gameConnectionRegistry.broadcastRoom(String.valueOf(roomIdObj), payload);
    }

    private void deliverRoomUserEvent(Map<String, Object> envelope, String payload) {
        Object roomIdObj = envelope.get("roomId");
        Object userIdObj = envelope.get("userId");
        if (roomIdObj == null || userIdObj == null) {
            return;
        }
        Long userId = toLong(userIdObj);
        if (userId == null) {
            return;
        }
        gameConnectionRegistry.sendToRoom(String.valueOf(roomIdObj), userId, payload);
    }

    private void deliverGameEvent(Map<String, Object> envelope, String payload) {
        Object gameCodeObj = envelope.get("gameCode");
        Object userIdObj = envelope.get("userId");
        if (gameCodeObj == null || userIdObj == null) {
            return;
        }
        Long userId = toLong(userIdObj);
        if (userId == null) {
            return;
        }
        gameConnectionRegistry.sendToGame(String.valueOf(gameCodeObj), userId, payload);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void publishRoomEvent(String roomId, String payload) {
        if (roomId == null || roomId.isBlank() || payload == null) {
            return;
        }
        try {
            String envelope = objectMapper.writeValueAsString(Map.of(
                    "sourceInstanceId", instanceId,
                    "targetType", TARGET_ROOM,
                    "roomId", roomId,
                    "payload", payload
            ));
            stringRedisTemplate.convertAndSend(Constant.GAME_ROOM_EVENT_CHANNEL, envelope);
        } catch (Exception e) {
            log.debug("发布游戏房间 Redis 事件失败 roomId={}, error={}", roomId, e.getMessage());
        }
    }

    @Override
    public void publishRoomUserEvent(String roomId, Long userId, String payload) {
        if (roomId == null || roomId.isBlank() || userId == null || payload == null) {
            return;
        }
        try {
            String envelope = objectMapper.writeValueAsString(Map.of(
                    "sourceInstanceId", instanceId,
                    "targetType", TARGET_ROOM_USER,
                    "roomId", roomId,
                    "userId", userId,
                    "payload", payload
            ));
            stringRedisTemplate.convertAndSend(Constant.GAME_ROOM_EVENT_CHANNEL, envelope);
        } catch (Exception e) {
            log.debug("发布游戏房间定向事件失败 roomId={}, userId={}, error={}", roomId, userId, e.getMessage());
        }
    }

    @Override
    public void publishGameEvent(String gameCode, Long userId, String payload) {
        if (gameCode == null || gameCode.isBlank() || userId == null || payload == null) {
            return;
        }
        try {
            String envelope = objectMapper.writeValueAsString(Map.of(
                    "sourceInstanceId", instanceId,
                    "targetType", TARGET_GAME,
                    "gameCode", gameCode,
                    "userId", userId,
                    "payload", payload
            ));
            stringRedisTemplate.convertAndSend(Constant.GAME_ROOM_EVENT_CHANNEL, envelope);
        } catch (Exception e) {
            log.debug("发布游戏大厅 Redis 事件失败 gameCode={}, userId={}, error={}", gameCode, userId, e.getMessage());
        }
    }

    @Override
    public void publishRoomCommand(String gameCode,
                                   String roomId,
                                   Long userId,
                                   String commandType,
                                   String requestId,
                                   String data) {
        if (gameCode == null || gameCode.isBlank()
                || roomId == null || roomId.isBlank()
                || userId == null
                || commandType == null || commandType.isBlank()) {
            return;
        }
        try {
            String envelope = objectMapper.writeValueAsString(Map.of(
                    "sourceInstanceId", instanceId,
                    "targetType", TARGET_COMMAND,
                    "gameCode", gameCode,
                    "roomId", roomId,
                    "userId", userId,
                    "commandType", commandType,
                    "requestId", requestId == null ? "" : requestId,
                    "data", data == null ? "" : data
            ));
            stringRedisTemplate.convertAndSend(Constant.GAME_ROOM_EVENT_CHANNEL, envelope);
        } catch (Exception e) {
            log.debug("发布游戏房间命令失败 gameCode={}, roomId={}, userId={}, type={}, error={}",
                    gameCode,
                    roomId,
                    userId,
                    commandType,
                    e.getMessage());
        }
    }
}
