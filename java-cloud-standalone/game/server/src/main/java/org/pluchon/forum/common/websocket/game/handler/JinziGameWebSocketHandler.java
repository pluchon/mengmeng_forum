package org.pluchon.forum.common.websocket.game.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.websocket.game.GameConnectionRegistry;
import org.pluchon.forum.common.websocket.game.GameWsMessage;
import org.pluchon.forum.common.websocket.game.GameWsResponse;
import org.pluchon.forum.service.impl.game.GameConstants;
import org.pluchon.forum.service.interfaces.game.GameOnlineStateService;
import org.pluchon.forum.service.interfaces.game.GameUserProfileService;
import org.pluchon.forum.service.interfaces.game.JinziMatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// 井字棋游戏级 WebSocket，负责匹配队列和匹配成功通知
@Slf4j
@ConditionalOnProperty(name = "forum.features.game-runtime", havingValue = "true")
@Component
public class JinziGameWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private JinziMatchService jinziMatchService;

    @Autowired
    private GameUserProfileService gameUserProfileService;

    @Autowired
    private GameOnlineStateService gameOnlineStateService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        Long userId = resolveUserId(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        gameConnectionRegistry.enterGame(GameConstants.JINZI, userId, session);
        gameOnlineStateService.enterGame(GameConstants.JINZI, userId);
        gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.ok(
                "game_ready",
                null,
                gameUserProfileService.getProfileVO(userId, GameConstants.JINZI)
        )));
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        Long userId = resolveUserId(session);
        if (userId == null) {
            return;
        }
        if ("ping".equalsIgnoreCase(message.getPayload())) {
            gameConnectionRegistry.touch(session);
            gameOnlineStateService.touchGame(GameConstants.JINZI, userId);
            gameConnectionRegistry.send(session, "pong");
            return;
        }
        GameWsMessage wsMessage = objectMapper.readValue(message.getPayload(), GameWsMessage.class);
        if ("ping".equals(wsMessage.getType())) {
            gameConnectionRegistry.touch(session);
            gameOnlineStateService.touchGame(GameConstants.JINZI, userId);
            gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.ok(
                    "pong",
                    wsMessage.getRequestId(),
                    null
            )));
            return;
        }
        if ("start_match".equals(wsMessage.getType())) {
            jinziMatchService.startMatch(userId, wsMessage.getRequestId(), session);
            return;
        }
        if ("stop_match".equals(wsMessage.getType())) {
            jinziMatchService.stopMatch(userId, wsMessage.getRequestId(), session);
            return;
        }
        gameConnectionRegistry.send(session, objectMapper.writeValueAsString(GameWsResponse.fail(
                "game_error",
                wsMessage.getRequestId(),
                "不支持的井字棋消息类型"
        )));
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        cleanupMatchSession(session);
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        cleanupMatchSession(session);
        log.debug("井字棋游戏 WS 异常 sessionId={}, error={}", session.getId(), exception.getMessage());
    }

    private void cleanupMatchSession(WebSocketSession session) {
        Long userId = resolveUserId(session);
        if (userId != null) {
            gameConnectionRegistry.exitGame(GameConstants.JINZI, userId, session);
            gameOnlineStateService.leaveGame(GameConstants.JINZI, userId);
            boolean removed = jinziMatchService.removeFromQueue(userId);
            if (removed) {
                gameUserProfileService.updateStatus(userId, GameConstants.JINZI, GameConstants.PROFILE_IDLE, null);
            }
        }
    }

    private Long resolveUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get(Constant.JWT_USER_ID);
        return userId instanceof Long ? (Long) userId : null;
    }
}
