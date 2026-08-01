package org.pluchon.forum.common.websocket.game.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.websocket.game.GameConnectionRegistry;
import org.pluchon.forum.common.websocket.game.GameWsMessage;
import org.pluchon.forum.common.websocket.game.GameWsResponse;
import org.pluchon.forum.entity.dto.game.GobangChatRequest;
import org.pluchon.forum.entity.dto.game.GobangMoveRequest;
import org.pluchon.forum.entity.vo.game.GobangRoomStateVO;
import org.pluchon.forum.service.impl.game.GameConstants;
import org.pluchon.forum.service.interfaces.game.GameOnlineStateService;
import org.pluchon.forum.service.interfaces.game.GameRoomEventBusService;
import org.pluchon.forum.service.interfaces.game.GobangRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// 五子棋房间 WebSocket，负责准备、落子、认输、重连和房间消息
@Slf4j
@ConditionalOnProperty(name = "forum.features.game-runtime", havingValue = "true")
@Component
public class GobangRoomWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private GobangRoomService gobangRoomService;

    @Autowired
    private GameOnlineStateService gameOnlineStateService;

    @Autowired
    private GameRoomEventBusService gameRoomEventBusService;

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
            gameOnlineStateService.enterGame(GameConstants.GOBANG, userId);
            GobangRoomStateVO state = gobangRoomService.joinRoom(roomId, userId, session);
            gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.ok(
                    "room_ready",
                    null,
                    state
            )));
        } catch (Exception e) {
            if (!proxyJoin(session, roomId, userId)) {
                // 房间可能已经结算并被内存清理，握手成功后也要给前端可处理的错误，避免 1011 异常断连。
                log.warn("五子棋房间连接失败 roomId={}, userId={}, error={}", roomId, userId, e.getMessage());
                gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.fail(
                        "room_error",
                        null,
                        "房间不存在或已结束，请返回大厅重新进入"
                )));
                session.close(CloseStatus.POLICY_VIOLATION);
            }
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
            gameOnlineStateService.touchGame(GameConstants.GOBANG, userId);
            gameConnectionRegistry.send(session, "pong");
            return;
        }
        GameWsMessage wsMessage = objectMapper.readValue(message.getPayload(), GameWsMessage.class);
        if ("ping".equals(wsMessage.getType())) {
            gameConnectionRegistry.touch(session);
            gameOnlineStateService.touchGame(GameConstants.GOBANG, userId);
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
            log.error("五子棋房间消息处理失败 roomId={}, userId={}, type={}, error={}",
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
        if ("move".equals(wsMessage.getType())) {
            if (!gobangRoomService.hasLocalRoom(roomId)) {
                publishProxyCommand(roomId, userId, wsMessage);
                return;
            }
            if (wsMessage.getData() == null || wsMessage.getData().isNull()) {
                gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.fail(
                        "move_rejected",
                        wsMessage.getRequestId(),
                        "落子参数不能为空"
                )));
                return;
            }
            GobangMoveRequest request = objectMapper.treeToValue(wsMessage.getData(), GobangMoveRequest.class);
            gobangRoomService.handleMove(roomId, userId, request.getRow(), request.getCol(), wsMessage.getRequestId());
            return;
        }
        if ("chat".equals(wsMessage.getType())) {
            if (!gobangRoomService.hasLocalRoom(roomId)) {
                publishProxyCommand(roomId, userId, wsMessage);
                return;
            }
            GobangChatRequest request = wsMessage.getData() == null || wsMessage.getData().isNull()
                    ? new GobangChatRequest()
                    : objectMapper.treeToValue(wsMessage.getData(), GobangChatRequest.class);
            gobangRoomService.chat(roomId, userId, request, wsMessage.getRequestId());
            return;
        }
        if ("surrender".equals(wsMessage.getType())) {
            if (!gobangRoomService.hasLocalRoom(roomId)) {
                publishProxyCommand(roomId, userId, wsMessage);
                return;
            }
            gobangRoomService.surrender(roomId, userId, wsMessage.getRequestId());
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
            if (gobangRoomService.hasLocalRoom(roomId)) {
                gobangRoomService.handleDisconnect(roomId, userId, session);
            } else {
                gameConnectionRegistry.exitRoom(roomId, userId, session);
                gameRoomEventBusService.publishRoomCommand(GameConstants.GOBANG, roomId, userId, "disconnect", null, null);
            }
            gameOnlineStateService.leaveGame(GameConstants.GOBANG, userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = resolveUserId(session);
        String roomId = resolveRoomId(session);
        if (userId != null && roomId != null) {
            if (gobangRoomService.hasLocalRoom(roomId)) {
                gobangRoomService.handleDisconnect(roomId, userId, session);
            } else {
                gameConnectionRegistry.exitRoom(roomId, userId, session);
                gameRoomEventBusService.publishRoomCommand(GameConstants.GOBANG, roomId, userId, "disconnect", null, null);
            }
            gameOnlineStateService.leaveGame(GameConstants.GOBANG, userId);
        }
        log.debug("五子棋房间 WS 异常 sessionId={}, error={}", session.getId(), exception.getMessage());
    }

    private Long resolveUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get(Constant.JWT_USER_ID);
        return userId instanceof Long ? (Long) userId : null;
    }

    private String resolveRoomId(WebSocketSession session) {
        String path = session.getUri() == null ? "" : session.getUri().getPath();
        String prefix = "/ws/games/gobang/rooms/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            return null;
        }
        return path.substring(prefix.length());
    }

    private boolean proxyJoin(WebSocketSession session, String roomId, Long userId) throws Exception {
        GobangRoomStateVO state = gobangRoomService.getRoomState(roomId, userId);
        if (state == null) {
            return false;
        }
        gameConnectionRegistry.enterRoom(roomId, userId, session);
        gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.ok(
                "room_ready",
                null,
                state
        )));
        gameRoomEventBusService.publishRoomCommand(GameConstants.GOBANG, roomId, userId, "state", null, null);
        return true;
    }

    private void publishProxyCommand(String roomId, Long userId, GameWsMessage wsMessage) throws Exception {
        gameRoomEventBusService.publishRoomCommand(
                GameConstants.GOBANG,
                roomId,
                userId,
                wsMessage.getType(),
                wsMessage.getRequestId(),
                wsMessage.getData() == null || wsMessage.getData().isNull()
                        ? null
                        : objectMapper.writeValueAsString(wsMessage.getData())
        );
    }
}
