package org.pluchon.forum.service.interfaces.game;

import org.pluchon.forum.entity.dto.game.TetrisChatRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.game.TetrisActiveRoomVO;
import org.pluchon.forum.entity.vo.game.TetrisRoomStateVO;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

// 俄罗斯方块 PK 房间服务
public interface TetrisRoomService {

    String createMatchedRoom(Long userIdA, Long userIdB);

    TetrisRoomStateVO joinRoom(String roomId, Long userId, WebSocketSession session);

    TetrisRoomStateVO getRoomState(String roomId, Long userId);

    boolean hasLocalRoom(String roomId);

    void pushRoomState(String roomId, String requestId);

    void handleInput(String roomId, Long userId, String action, String requestId);

    void chat(String roomId, Long userId, TetrisChatRequest request, String requestId);

    void surrender(String roomId, Long userId, String requestId);

    void handleDisconnect(String roomId, Long userId, WebSocketSession session);

    /**
     * 可观战房间分页。
     *
     * <p>roomId 非空时按房间号精确查询——原来是把全量房间下发给前端再本地过滤，
     * 房间一多响应体会线性膨胀，而且「搜索」只能搜到已加载的那批。
     */
    PageResult<TetrisActiveRoomVO> pageActiveRooms(String roomId, Integer pageNum, Integer pageSize);
}
