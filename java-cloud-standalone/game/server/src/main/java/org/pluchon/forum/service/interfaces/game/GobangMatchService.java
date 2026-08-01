package org.pluchon.forum.service.interfaces.game;

import org.springframework.web.socket.WebSocketSession;

// 五子棋匹配业务接口
public interface GobangMatchService {

    void startMatch(Long userId, String requestId, WebSocketSession session);

    void stopMatch(Long userId, String requestId, WebSocketSession session);

    boolean removeFromQueue(Long userId);
}
