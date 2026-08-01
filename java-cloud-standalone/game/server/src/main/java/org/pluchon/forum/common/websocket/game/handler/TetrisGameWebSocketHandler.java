package org.pluchon.forum.common.websocket.game.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.websocket.game.GameConnectionRegistry;
import org.pluchon.forum.common.websocket.game.GameWsMessage;
import org.pluchon.forum.common.websocket.game.GameWsResponse;
import org.pluchon.forum.service.impl.game.GameConstants;
import org.pluchon.forum.service.interfaces.game.GameOnlineStateService;
import org.pluchon.forum.service.interfaces.game.GameUserProfileService;
import org.pluchon.forum.service.interfaces.game.TetrisMatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// 俄罗斯方块 PK 游戏级 WebSocket
@Slf4j
@ConditionalOnProperty(name = "forum.features.game-runtime", havingValue = "true")
@Component
public class TetrisGameWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private TetrisMatchService tetrisMatchService;

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
        gameConnectionRegistry.enterGame(GameConstants.TETRIS_PK, userId, session);
        gameOnlineStateService.enterGame(GameConstants.TETRIS_PK, userId);
        tetrisMatchService.reconcileMatchingState(userId);
        gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.ok(
                "game_ready",
                null,
                gameUserProfileService.getProfileVO(userId, GameConstants.TETRIS_PK)
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
        if ("start_match".equals(wsMessage.getType())) {
            tetrisMatchService.startMatch(userId, wsMessage.getRequestId(), session);
            return;
        }
        if ("stop_match".equals(wsMessage.getType())) {
            tetrisMatchService.stopMatch(userId, wsMessage.getRequestId(), session);
            return;
        }
        gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.fail(
                "game_error",
                wsMessage.getRequestId(),
                "不支持的俄罗斯方块消息类型"
        )));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = resolveUserId(session);
        if (userId != null) {
            gameConnectionRegistry.exitGame(GameConstants.TETRIS_PK, userId, session);
            gameOnlineStateService.leaveGame(GameConstants.TETRIS_PK, userId);
            boolean removed = tetrisMatchService.removeFromQueue(userId);
            if (removed) {
                gameUserProfileService.updateStatus(userId, GameConstants.TETRIS_PK, GameConstants.PROFILE_IDLE, null);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = resolveUserId(session);
        if (userId != null) {
            gameConnectionRegistry.exitGame(GameConstants.TETRIS_PK, userId, session);
            gameOnlineStateService.leaveGame(GameConstants.TETRIS_PK, userId);
            boolean removed = tetrisMatchService.removeFromQueue(userId);
            if (removed) {
                gameUserProfileService.updateStatus(userId, GameConstants.TETRIS_PK, GameConstants.PROFILE_IDLE, null);
            }
        }
        log.debug("俄罗斯方块游戏 WS 异常 sessionId={}, error={}", session.getId(), exception.getMessage());
    }

    private Long resolveUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get(Constant.JWT_USER_ID);
        return userId instanceof Long ? (Long) userId : null;
    }
}
