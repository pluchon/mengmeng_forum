package org.pluchon.forum.service.impl.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.utils.OnlineUserManageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.Map;

// 接收消息，进行跨实例传输
@Service
@Slf4j
public class WebSocketPushService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private OnlineUserManageUtil onlineUserManageUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @PostConstruct
    public void subscribe() {
        MessageListener listener = (message, pattern) -> {
            try {
                String body = new String(message.getBody());
                @SuppressWarnings("unchecked")
                Map<String, Object> envelope = objectMapper.readValue(body, Map.class);
                Object uidObj = envelope.get("userId");
                Object payloadObj = envelope.get("payload");
                if (uidObj == null || payloadObj == null) {
                    log.debug("[WS推送订阅] userId或payload为空，跳过");
                    return;
                }
                Long userId = uidObj instanceof Number n ? n.longValue() : Long.parseLong(uidObj.toString());
                String payload = payloadObj instanceof String s ? s : objectMapper.writeValueAsString(payloadObj);
                log.debug("[WS推送订阅] 收到Redis消息 targetUserId={} payloadLength={}", userId, payload.length());
                boolean delivered = pushLocal(userId, payload);
                log.debug("[WS推送订阅] 投递结果 targetUserId={} delivered={}", userId, delivered);
            } catch (Exception e) {
                log.warn("[WS推送订阅] 处理失败: {}", e.getMessage(), e);
            }
        };
        redisMessageListenerContainer.addMessageListener(listener, new ChannelTopic(Constant.WS_PUSH_CHANNEL));
    }

    // 向目标用户推送 经 Redis 广播，各节点对本机在线 Session 投递
    public void push(Long userId, String payload) {
        if (userId == null || payload == null) {
            return;
        }
        boolean delivered = pushLocal(userId, payload);
        if (delivered) {
            log.debug("[WS推送] 本机直投成功 targetUserId={} payloadLength={}", userId, payload.length());
            return;
        }
        try {
            String envelope = objectMapper.writeValueAsString(Map.of("userId", userId, "payload", payload));
            stringRedisTemplate.convertAndSend(Constant.WS_PUSH_CHANNEL, envelope);
            log.debug("[WS推送] 本机未命中，已发布Redis targetUserId={} payloadLength={}", userId, payload.length());
        } catch (Exception e) {
            log.warn("WS 推送发布失败 userId={}: {}", userId, e.getMessage());
        }
    }

    // 仅本机投递 不经 Redis，供订阅回调使用
    private boolean pushLocal(Long userId, String payload) {
        boolean result = onlineUserManageUtil.sendMessage(userId, payload);
        if (!result) {
            log.debug("[WS推送本机] 用户不在线或投递失败 userId={} online={}", userId, onlineUserManageUtil.isOnline(userId));
        }
        return result;
    }
}
