package org.example.forumdemo.common.advice;

import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.utils.OnlineUserManageUtil;
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

    //我的结论是，我们websocket处理的是聊天，一旦我们前端退出聊天界面，这个session就会断
    //当我们再次打开，session就会连接成功
    //我们引入消息队列，如果我和对方没有建立起连接，这个消息就会跑到rabbitmq的队列中等待被消费
    //私信界面，如果我和对方都在线，则我们的websocket建立了连接
    //一旦对方下线，就算对方不在线，我们把消息放入数据库，直接从数据库加载消息了

    @Autowired
    private OnlineUserManageUtil onlineUserManageUtil;

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
        }
    }

    // 连接异常：下线清理
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        cleanup(session);
        Long userId = resolveUserId(session);
        if (userId != null) {
            onlineUserManageUtil.offline(userId, session);
        }
        log.error("[WebSocket] 连接异常 | sessionId={} | error={}", session.getId(), exception.getMessage());
    }

    // 连接关闭：下线清理
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        cleanup(session);
        Long userId = resolveUserId(session);
        if (userId != null) {
            onlineUserManageUtil.offline(userId, session);
            log.debug("[WebSocket] 用户下线 | userId={} | status={}", userId, status);
        }
    }

    // 每 30 秒扫描一次，踢出超过 90 秒没有心跳的死连接
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

    // 从 attributes 取 userId
    // 由 TokenHandshakeInterceptor 鉴权后写入，数据可信
    private Long resolveUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get(Constant.JWT_USER_ID);
        return userId instanceof Long ? (Long) userId : null;
    }
}
