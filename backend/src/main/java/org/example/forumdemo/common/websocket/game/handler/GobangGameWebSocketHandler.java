package org.example.forumdemo.common.websocket.game.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.websocket.game.GameConnectionRegistry;
import org.example.forumdemo.common.websocket.game.GameWsMessage;
import org.example.forumdemo.common.websocket.game.GameWsResponse;
import org.example.forumdemo.service.impl.game.GameConstants;
import org.example.forumdemo.service.interfaces.game.GameOnlineStateService;
import org.example.forumdemo.service.interfaces.game.GameUserProfileService;
import org.example.forumdemo.service.interfaces.game.GobangMatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// 五子棋游戏级 WebSocket，负责开始匹配、取消匹配和匹配成功通知
@Slf4j
@Component
public class GobangGameWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private GobangMatchService gobangMatchService;

    @Autowired
    private GameUserProfileService gameUserProfileService;

    @Autowired
    private GameOnlineStateService gameOnlineStateService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = resolveUserId(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        gameConnectionRegistry.enterGame(GameConstants.GOBANG, userId, session);
        gameOnlineStateService.enterGame(GameConstants.GOBANG, userId);
        gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.ok(
                "game_ready",
                null,
                gameUserProfileService.getProfileVO(userId, GameConstants.GOBANG)
        )));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = resolveUserId(session);
        if (userId == null) {
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
        if ("start_match".equals(wsMessage.getType())) {
            gobangMatchService.startMatch(userId, wsMessage.getRequestId(), session);
            return;
        }
        if ("stop_match".equals(wsMessage.getType())) {
            gobangMatchService.stopMatch(userId, wsMessage.getRequestId(), session);
            return;
        }
        gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.fail(
                "game_error",
                wsMessage.getRequestId(),
                "不支持的五子棋消息类型"
        )));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = resolveUserId(session);
        if (userId != null) {
            gameConnectionRegistry.exitGame(GameConstants.GOBANG, userId, session);
            gameOnlineStateService.leaveGame(GameConstants.GOBANG, userId);
            boolean removed = gobangMatchService.removeFromQueue(userId);
            if (removed) {
                gameUserProfileService.updateStatus(userId, GameConstants.GOBANG, GameConstants.PROFILE_IDLE, null);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = resolveUserId(session);
        if (userId != null) {
            gameConnectionRegistry.exitGame(GameConstants.GOBANG, userId, session);
            gameOnlineStateService.leaveGame(GameConstants.GOBANG, userId);
            boolean removed = gobangMatchService.removeFromQueue(userId);
            if (removed) {
                gameUserProfileService.updateStatus(userId, GameConstants.GOBANG, GameConstants.PROFILE_IDLE, null);
            }
        }
        log.debug("五子棋游戏 WS 异常 sessionId={}, error={}", session.getId(), exception.getMessage());
    }

    private Long resolveUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get(Constant.JWT_USER_ID);
        return userId instanceof Long ? (Long) userId : null;
    }
}
