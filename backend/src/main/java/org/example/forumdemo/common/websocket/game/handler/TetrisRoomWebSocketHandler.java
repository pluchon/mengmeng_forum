package org.example.forumdemo.common.websocket.game.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.websocket.game.GameConnectionRegistry;
import org.example.forumdemo.common.websocket.game.GameWsMessage;
import org.example.forumdemo.common.websocket.game.GameWsResponse;
import org.example.forumdemo.entity.dto.game.TetrisChatRequest;
import org.example.forumdemo.entity.dto.game.TetrisInputRequest;
import org.example.forumdemo.entity.vo.game.TetrisRoomStateVO;
import org.example.forumdemo.service.impl.game.GameConstants;
import org.example.forumdemo.service.interfaces.game.GameOnlineStateService;
import org.example.forumdemo.service.interfaces.game.TetrisRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// 俄罗斯方块 PK 房间 WebSocket
@Slf4j
@Component
public class TetrisRoomWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private TetrisRoomService tetrisRoomService;

    @Autowired
    private GameOnlineStateService gameOnlineStateService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = resolveUserId(session);
        String roomId = resolveRoomId(session);
        if (userId == null || roomId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            gameOnlineStateService.enterGame(GameConstants.TETRIS_PK, userId);
            TetrisRoomStateVO state = tetrisRoomService.joinRoom(roomId, userId, session);
            gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.ok(
                    "room_ready",
                    null,
                    state
            )));
        } catch (Exception e) {
            log.warn("俄罗斯方块房间连接失败 roomId={}, userId={}, error={}", roomId, userId, e.getMessage());
            gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.fail(
                    "room_error",
                    null,
                    "房间不存在或已结束，请返回大厅重新进入"
            )));
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = resolveUserId(session);
        String roomId = resolveRoomId(session);
        if (userId == null || roomId == null) {
            return;
        }
        if ("ping".equalsIgnoreCase(message.getPayload())) {
            gameConnectionRegistry.touch(session);
            gameOnlineStateService.touchGame(GameConstants.TETRIS_PK, userId);
            gameConnectionRegistry.send(session, "pong");
            return;
        }
        GameWsMessage wsMessage = objectMapper.readValue(message.getPayload(), GameWsMessage.class);
        if ("ping".equals(wsMessage.getType())) {
            gameConnectionRegistry.touch(session);
            gameOnlineStateService.touchGame(GameConstants.TETRIS_PK, userId);
            gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.ok(
                    "pong",
                    wsMessage.getRequestId(),
                    null
            )));
            return;
        }
        try {
            handleRoomMessage(session, roomId, userId, wsMessage);
        } catch (Exception e) {
            log.error("俄罗斯方块房间消息处理失败 roomId={}, userId={}, type={}, error={}",
                    roomId, userId, wsMessage.getType(), e.getMessage(), e);
            gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.fail(
                    "room_error",
                    wsMessage.getRequestId(),
                    "房间消息处理失败，请刷新房间后重试"
            )));
        }
    }

    private void handleRoomMessage(WebSocketSession session,
                                   String roomId,
                                   Long userId,
                                   GameWsMessage wsMessage) throws Exception {
        if ("input".equals(wsMessage.getType())) {
            TetrisInputRequest request = wsMessage.getData() == null || wsMessage.getData().isNull()
                    ? new TetrisInputRequest()
                    : objectMapper.treeToValue(wsMessage.getData(), TetrisInputRequest.class);
            String action = request.getAction() == null ? "" : request.getAction().trim().toLowerCase();
            tetrisRoomService.handleInput(roomId, userId, action, wsMessage.getRequestId());
            return;
        }
        if ("chat".equals(wsMessage.getType())) {
            TetrisChatRequest request = wsMessage.getData() == null || wsMessage.getData().isNull()
                    ? new TetrisChatRequest()
                    : objectMapper.treeToValue(wsMessage.getData(), TetrisChatRequest.class);
            tetrisRoomService.chat(roomId, userId, request, wsMessage.getRequestId());
            return;
        }
        if ("surrender".equals(wsMessage.getType())) {
            tetrisRoomService.surrender(roomId, userId, wsMessage.getRequestId());
            return;
        }
        gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.fail(
                "room_error",
                wsMessage.getRequestId(),
                "不支持的房间消息类型"
        )));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = resolveUserId(session);
        String roomId = resolveRoomId(session);
        if (userId != null && roomId != null) {
            tetrisRoomService.handleDisconnect(roomId, userId, session);
            gameOnlineStateService.leaveGame(GameConstants.TETRIS_PK, userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = resolveUserId(session);
        String roomId = resolveRoomId(session);
        if (userId != null && roomId != null) {
            tetrisRoomService.handleDisconnect(roomId, userId, session);
            gameOnlineStateService.leaveGame(GameConstants.TETRIS_PK, userId);
        }
        log.debug("俄罗斯方块房间 WS 异常 sessionId={}, error={}", session.getId(), exception.getMessage());
    }

    private Long resolveUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get(Constant.JWT_USER_ID);
        return userId instanceof Long ? (Long) userId : null;
    }

    private String resolveRoomId(WebSocketSession session) {
        String path = session.getUri() == null ? "" : session.getUri().getPath();
        String prefix = "/ws/games/tetris/rooms/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            return null;
        }
        return path.substring(prefix.length());
    }
}
