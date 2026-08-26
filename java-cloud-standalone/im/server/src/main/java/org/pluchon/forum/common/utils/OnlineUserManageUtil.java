package org.pluchon.forum.common.utils;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

// 在线用户管理：维护 userId > WebSocketSession 的映射，单设备登录
@Component
public class OnlineUserManageUtil {

    // key userId，value 对应的 WebSocket 会话
    private final ConcurrentHashMap<Long, WebSocketSession> manage = new ConcurrentHashMap<>();

    // 用户上线：新连接覆盖旧连接，避免消息继续推到旧页面
    public void online(Long userId, WebSocketSession webSocketSession) {
        if (userId == null || webSocketSession == null) {
            return;
        }
        WebSocketSession existing = manage.put(userId, webSocketSession);
        if (existing != null && existing != webSocketSession && existing.isOpen()) {
            try {
                existing.close(CloseStatus.NORMAL);
            } catch (Exception ignored) {
                // 旧连接关闭失败不影响新连接接管推送
            }
        }
    }

    // 用户下线：只允许移除自己的会话，防止多开时误删他人
    public void offline(Long userId, WebSocketSession webSocketSession) {
        WebSocketSession socketSession = manage.get(userId);
        if (webSocketSession != socketSession){
            return;
        }
        manage.remove(userId);
    }

    // 用户是否在线 WebSocket 已连接
    public boolean isOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        WebSocketSession session = manage.get(userId);
        return session != null && session.isOpen();
    }

    // 向指定用户推送文本消息，用户不在线返回 false
    public boolean sendMessage(Long userId, String payload) {
        WebSocketSession session = manage.get(userId);
        if (session == null || !session.isOpen()){
            return false;
        }
        try {
            session.sendMessage(new TextMessage(payload));
            return true;
        } catch (Exception e) {
            // 推送失败说明 Session 已失效，移除避免下次重复尝试
            manage.remove(userId);
            return false;
        }
    }
}
