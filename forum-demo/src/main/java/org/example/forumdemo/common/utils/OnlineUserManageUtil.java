package org.example.forumdemo.common.utils;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

// 在线用户管理：维护 userId -> WebSocketSession 的映射，单设备登录
@Component
public class OnlineUserManageUtil {

    // key=userId，value=对应的 WebSocket 会话
    private final ConcurrentHashMap<Long, WebSocketSession> manage = new ConcurrentHashMap<>();

    // 用户上线：已存在且连接正常则跳过，防多开；旧 session 已失效（网络闪断）则覆盖
    public void online(Long userId, WebSocketSession webSocketSession) {
        WebSocketSession existing = manage.get(userId);
        if (existing != null && existing.isOpen()) {
            return;
        }
        manage.put(userId, webSocketSession);
    }

    // 用户下线：只允许移除自己的会话，防止多开时误删他人
    public void offline(Long userId, WebSocketSession webSocketSession) {
        WebSocketSession socketSession = manage.get(userId);
        if (webSocketSession != socketSession){
            return;
        }
        manage.remove(userId);
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
