package org.example.forumdemo.service.interfaces.game;

import org.example.forumdemo.entity.vo.game.GobangRoomStateVO;
import org.example.forumdemo.entity.vo.game.GobangActiveRoomVO;
import org.example.forumdemo.entity.dto.game.GobangChatRequest;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

// 五子棋房间业务接口
public interface GobangRoomService {

    String createMatchedRoom(Long userIdA, Long userIdB);

    String createAiRoom(Long userId);

    GobangRoomStateVO joinRoom(String roomId, Long userId, WebSocketSession session);

    GobangRoomStateVO getRoomState(String roomId, Long userId);

    boolean hasLocalRoom(String roomId);

    void pushRoomState(String roomId, String requestId);

    void handleMove(String roomId, Long userId, Integer row, Integer col, String requestId);

    void surrender(String roomId, Long userId, String requestId);

    void chat(String roomId, Long userId, GobangChatRequest request, String requestId);

    void handleDisconnect(String roomId, Long userId, WebSocketSession session);

    List<GobangActiveRoomVO> listActiveRooms();
}
