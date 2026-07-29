package org.example.forumdemo.service.interfaces.game;

import org.example.forumdemo.entity.dto.game.GobangChatRequest;
import org.example.forumdemo.entity.vo.game.JinziRoomStateVO;
import org.springframework.web.socket.WebSocketSession;

// 井字棋房间业务接口，不开放观战功能
public interface JinziRoomService {

    String createMatchedRoom(Long userIdA, Long userIdB);

    JinziRoomStateVO joinRoom(String roomId, Long userId, WebSocketSession session);

    JinziRoomStateVO getRoomState(String roomId, Long userId);

    boolean hasLocalRoom(String roomId);

    void pushRoomState(String roomId, String requestId);

    void handleMove(String roomId, Long userId, Integer row, Integer col, String requestId);

    void surrender(String roomId, Long userId, String requestId);

    void chat(String roomId, Long userId, GobangChatRequest request, String requestId);

    void handleDisconnect(String roomId, Long userId, WebSocketSession session);
}
