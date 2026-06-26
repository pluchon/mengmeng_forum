package org.example.forumdemo.common.config;

import org.example.forumdemo.common.advice.WebSocket;
import org.example.forumdemo.common.websocket.game.handler.*;
import org.example.forumdemo.common.interceptor.TokenHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

//配置握手前的JWT鉴权
@Configuration
@EnableWebSocket
public class WebSocketConfigure implements WebSocketConfigurer {

    @Autowired
    private WebSocket webSocket;

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

        registry.addHandler(webSocket, "/ws/notify")
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins(origins);
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
