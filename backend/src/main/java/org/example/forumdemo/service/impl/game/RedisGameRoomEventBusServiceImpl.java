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
                Object roomIdObj = envelope.get("roomId");
                Object payloadObj = envelope.get("payload");
                if (roomIdObj == null || payloadObj == null) {
                    return;
                }
                String payload = payloadObj instanceof String s
                        ? s
                        : objectMapper.writeValueAsString(payloadObj);
                gameConnectionRegistry.broadcastRoom(String.valueOf(roomIdObj), payload);
            } catch (Exception e) {
                log.debug("游戏房间 Redis 事件处理失败: {}", e.getMessage());
            }
        };
        redisMessageListenerContainer.addMessageListener(listener, new ChannelTopic(Constant.GAME_ROOM_EVENT_CHANNEL));
    }

    @Override
    public void publishRoomEvent(String roomId, String payload) {
        if (roomId == null || roomId.isBlank() || payload == null) {
            return;
        }
        try {
            String envelope = objectMapper.writeValueAsString(Map.of(
                    "sourceInstanceId", instanceId,
                    "roomId", roomId,
                    "payload", payload
            ));
            stringRedisTemplate.convertAndSend(Constant.GAME_ROOM_EVENT_CHANNEL, envelope);
        } catch (Exception e) {
            log.debug("发布游戏房间 Redis 事件失败 roomId={}, error={}", roomId, e.getMessage());
        }
    }
}
