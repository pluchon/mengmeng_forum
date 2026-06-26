package org.example.forumdemo.service.interfaces.game;

import org.example.forumdemo.entity.dto.game.TetrisChatRequest;
import org.example.forumdemo.entity.vo.game.TetrisActiveRoomVO;
import org.example.forumdemo.entity.vo.game.TetrisRoomStateVO;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

// 俄罗斯方块 PK 房间服务
public interface TetrisRoomService {

    String createMatchedRoom(Long userIdA, Long userIdB);

    TetrisRoomStateVO joinRoom(String roomId, Long userId, WebSocketSession session);

    TetrisRoomStateVO getRoomState(String roomId, Long userId);

    void handleInput(String roomId, Long userId, String action, String requestId);

    void chat(String roomId, Long userId, TetrisChatRequest request, String requestId);

    void surrender(String roomId, Long userId, String requestId);

    void handleDisconnect(String roomId, Long userId, WebSocketSession session);

    List<TetrisActiveRoomVO> listActiveRooms();
}
