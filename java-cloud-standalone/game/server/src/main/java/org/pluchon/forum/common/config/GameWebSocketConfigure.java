package org.pluchon.forum.common.config;

import org.pluchon.forum.common.interceptor.TokenHandshakeInterceptor;
import org.pluchon.forum.common.websocket.game.handler.GameCenterLobbyWebSocketHandler;
import org.pluchon.forum.common.websocket.game.handler.GobangGameWebSocketHandler;
import org.pluchon.forum.common.websocket.game.handler.GobangRoomWebSocketHandler;
import org.pluchon.forum.common.websocket.game.handler.JinziGameWebSocketHandler;
import org.pluchon.forum.common.websocket.game.handler.JinziRoomWebSocketHandler;
import org.pluchon.forum.common.websocket.game.handler.TetrisGameWebSocketHandler;
import org.pluchon.forum.common.websocket.game.handler.TetrisRoomWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

// 游戏中心 / 对局 WebSocket
@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "forum.features.game-runtime", havingValue = "true")
public class GameWebSocketConfigure implements WebSocketConfigurer {

    @Autowired
    private TokenHandshakeInterceptor tokenHandshakeInterceptor;

    @Autowired
    private GameCenterLobbyWebSocketHandler gameCenterLobbyWebSocketHandler;

    @Autowired
    private GobangGameWebSocketHandler gobangGameWebSocketHandler;

    @Autowired
    private GobangRoomWebSocketHandler gobangRoomWebSocketHandler;

    @Autowired
    private JinziGameWebSocketHandler jinziGameWebSocketHandler;

    @Autowired
    private JinziRoomWebSocketHandler jinziRoomWebSocketHandler;

    @Autowired
    private TetrisGameWebSocketHandler tetrisGameWebSocketHandler;

    @Autowired
    private TetrisRoomWebSocketHandler tetrisRoomWebSocketHandler;

    @Value("${forum.websocket.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOriginsCsv;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
        if (origins.length == 0) {
            origins = new String[] {"http://localhost:5173"};
        }
        registry.addHandler(gameCenterLobbyWebSocketHandler, "/ws/game-center/lobby")
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins(origins);
        registry.addHandler(gobangGameWebSocketHandler, "/ws/games/gobang")
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins(origins);
        registry.addHandler(gobangRoomWebSocketHandler, "/ws/games/gobang/rooms/*")
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins(origins);
        registry.addHandler(jinziGameWebSocketHandler, "/ws/games/jinzi")
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins(origins);
        registry.addHandler(jinziRoomWebSocketHandler, "/ws/games/jinzi/rooms/*")
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins(origins);
        registry.addHandler(tetrisGameWebSocketHandler, "/ws/games/tetris")
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins(origins);
        registry.addHandler(tetrisRoomWebSocketHandler, "/ws/games/tetris/rooms/*")
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins(origins);
    }
}
