package org.example.forumdemo.service.interfaces.game;

import org.springframework.web.socket.WebSocketSession;

// 俄罗斯方块 PK 匹配服务
public interface TetrisMatchService {

    void startMatch(Long userId, String requestId, WebSocketSession session);

    void stopMatch(Long userId, String requestId, WebSocketSession session);

    boolean removeFromQueue(Long userId);

    // 连接建立时校正「匹配中」与队列是否一致
    void reconcileMatchingState(Long userId);
}
