package org.pluchon.forum.service.interfaces.game;

import org.pluchon.forum.entity.vo.game.GobangRoomStateVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.game.GobangActiveRoomVO;
import org.pluchon.forum.entity.dto.game.GobangChatRequest;
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

    /**
     * 可观战房间分页。
     *
     * <p>roomId 非空时按房间号精确查询——原来是把全量房间下发给前端再本地过滤，
     * 房间一多响应体会线性膨胀，而且「搜索」只能搜到已加载的那批。
     */
    PageResult<GobangActiveRoomVO> pageActiveRooms(String roomId, Integer pageNum, Integer pageSize);
}
