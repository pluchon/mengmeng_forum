package org.example.forumdemo.common.websocket.game.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.websocket.game.GameConnectionRegistry;
import org.example.forumdemo.common.websocket.game.GameWsMessage;
import org.example.forumdemo.common.websocket.game.GameWsResponse;
import org.example.forumdemo.entity.dto.game.GobangChatRequest;
import org.example.forumdemo.entity.dto.game.GobangMoveRequest;
import org.example.forumdemo.entity.vo.game.GobangRoomStateVO;
import org.example.forumdemo.service.interfaces.game.GobangRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// 五子棋房间 WebSocket，负责准备、落子、认输、重连和房间消息
@Slf4j
@Component
public class GobangRoomWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private GobangRoomService gobangRoomService;

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
            GobangRoomStateVO state = gobangRoomService.joinRoom(roomId, userId, session);
            gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.ok(
                    "room_ready",
                    null,
                    state
            )));
        } catch (Exception e) {
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

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = resolveUserId(session);
        String roomId = resolveRoomId(session);
        if (userId == null || roomId == null) {
            return;
        }
        if ("ping".equalsIgnoreCase(message.getPayload())) {
            gameConnectionRegistry.touch(session);
            gameConnectionRegistry.send(session, "pong");
            return;
        }
        GameWsMessage wsMessage = objectMapper.readValue(message.getPayload(), GameWsMessage.class);
        if ("ping".equals(wsMessage.getType())) {
            gameConnectionRegistry.touch(session);
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
            GobangChatRequest request = wsMessage.getData() == null || wsMessage.getData().isNull()
                    ? new GobangChatRequest()
                    : objectMapper.treeToValue(wsMessage.getData(), GobangChatRequest.class);
            gobangRoomService.chat(roomId, userId, request, wsMessage.getRequestId());
            return;
        }
        if ("surrender".equals(wsMessage.getType())) {
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
            gobangRoomService.handleDisconnect(roomId, userId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = resolveUserId(session);
        String roomId = resolveRoomId(session);
        if (userId != null && roomId != null) {
            gobangRoomService.handleDisconnect(roomId, userId, session);
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
}
