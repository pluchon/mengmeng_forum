package org.example.forumdemo.common.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.utils.OnlineUserManageUtil;
import org.example.forumdemo.entity.dto.groupchat.GroupVoiceSignalRequest;
import org.example.forumdemo.service.interfaces.groupchat.GroupVoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;

// 文本 WebSocket 处理器，userId 由 TokenHandshakeInterceptor 鉴权后存入 attributes，此处直接取用
@Component
@Slf4j
public class WebSocket extends TextWebSocketHandler {

    @Autowired
    private OnlineUserManageUtil onlineUserManageUtil;

    @Autowired
    private GroupVoiceService groupVoiceService;

    @Autowired
    private ObjectMapper objectMapper;

    // 心跳超时阈值：90 秒内未收到 ping 则视为死连接
    private static final long HEARTBEAT_TIMEOUT_MS = 90_000;

    // 活跃 session 集合，用于心跳扫描
    private final ConcurrentHashMap<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    // 记录每个 session 最后一次收到 ping 的时间戳
    private final ConcurrentHashMap<String, Long> lastPingTime = new ConcurrentHashMap<>();

    // 连接建立：从 attributes 取出已鉴权的 userId 并注册上线
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = resolveUserId(session);
        if (userId == null) {
            log.warn("[WebSocket] 拒绝连接：缺少 userId | sessionId={}", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        activeSessions.put(session.getId(), session);
        lastPingTime.put(session.getId(), System.currentTimeMillis());
        onlineUserManageUtil.online(userId, session);
        log.debug("[WebSocket] 用户上线 | userId={}", userId);
    }

    // 收到客户端消息：处理心跳 ping/pong，更新最后活跃时间
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if ("ping".equalsIgnoreCase(message.getPayload())) {
            lastPingTime.put(session.getId(), System.currentTimeMillis());
            session.sendMessage(new TextMessage("pong"));
            return;
        }
        handleBusinessMessage(session, message.getPayload());
    }

    private void handleBusinessMessage(WebSocketSession session, String payload) {
        Long userId = resolveUserId(session);
        if (userId == null || payload == null || payload.isBlank()) {
            return;
        }
        try {
            GroupVoiceSignalEnvelope envelope = objectMapper.readValue(payload, GroupVoiceSignalEnvelope.class);
            if (!"group_voice_signal".equals(envelope.getType())) {
                return;
            }
            log.debug("[WebSocket] 收到语音信令 | from={} | target={} | roomVersion={} | type={}",
                    userId, envelope.getTargetUserId(), envelope.getRoomVersion(), envelope.getSignalType());
            groupVoiceService.handleSignal(envelope, userId);
        } catch (Exception e) {
            log.debug("[WebSocket] 业务消息处理失败 | sessionId={} | error={}", session.getId(), e.getMessage(), e);
        }
    }

    // 连接异常：下线清理
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        cleanup(session);
        Long userId = resolveUserId(session);
        if (userId != null) {
            onlineUserManageUtil.offline(userId, session);
        }
        log.error("[WebSocket] 连接异常 | sessionId={} | error={}", session.getId(), exception.getMessage());
    }

    // 连接关闭：下线清理
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cleanup(session);
        Long userId = resolveUserId(session);
        if (userId != null) {
            onlineUserManageUtil.offline(userId, session);
            log.debug("[WebSocket] 用户下线 | userId={} | status={}", userId, status);
        }
    }

    // 每 30 秒扫描一次，踢出超过 90 秒没有心跳的死连接
    // 如果客户端因为各种原因的连接断开，如果不及时的清理，就会导致资源极大的占用与浪费
    @Scheduled(fixedDelay = 30_000)
    public void evictStaleSessions() {
        long now = System.currentTimeMillis();
        lastPingTime.forEach((sessionId, lastTime) -> {
            if (now - lastTime > HEARTBEAT_TIMEOUT_MS) {
                WebSocketSession session = activeSessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        log.warn("[WebSocket] 心跳超时，踢出死连接 | sessionId={}", sessionId);
                        session.close(CloseStatus.SESSION_NOT_RELIABLE);
                        // afterConnectionClosed 会自动触发 cleanup 和 offline
                    } catch (Exception e) {
                        log.error("[WebSocket] 踢出失败，强制清理 | sessionId={} | error={}", sessionId, e.getMessage());
                        cleanup(session);
                    }
                } else {
                    // session 已关闭但未清理（异常断开场景），直接移除
                    lastPingTime.remove(sessionId);
                    activeSessions.remove(sessionId);
                }
            }
        });
    }

    // 清理 session 相关的本地状态
    private void cleanup(WebSocketSession session) {
        activeSessions.remove(session.getId());
        lastPingTime.remove(session.getId());
    }

    // 校验用户ID是否是Long类型，避免我们的空指针异常
    private Long resolveUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get(Constant.JWT_USER_ID);
        return userId instanceof Long ? (Long) userId : null;
    }

    // 群语音信令外壳
    private static class GroupVoiceSignalEnvelope extends GroupVoiceSignalRequest {
        private String type;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
