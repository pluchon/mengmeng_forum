package org.example.forumdemo.common.config;

import org.example.forumdemo.common.advice.WebSocket;
import org.example.forumdemo.common.websocket.game.handler.GameCenterLobbyWebSocketHandler;
import org.example.forumdemo.common.websocket.game.handler.GobangGameWebSocketHandler;
import org.example.forumdemo.common.websocket.game.handler.GobangRoomWebSocketHandler;
import org.example.forumdemo.common.websocket.game.handler.JinziGameWebSocketHandler;
import org.example.forumdemo.common.websocket.game.handler.JinziRoomWebSocketHandler;
import org.example.forumdemo.common.interceptor.TokenHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

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

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocket, "/ws/notify")
                // 握手前执行 JWT 鉴权
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(gameCenterLobbyWebSocketHandler, "/ws/game-center/lobby")
                // 游戏中心大厅连接，独立于通知连接
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(gobangGameWebSocketHandler, "/ws/games/gobang")
                // 五子棋游戏级连接，负责匹配
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(gobangRoomWebSocketHandler, "/ws/games/gobang/rooms/*")
                // 五子棋房间连接，负责落子和房间状态
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(jinziGameWebSocketHandler, "/ws/games/jinzi")
                // 井字棋游戏级连接，负责匹配
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(jinziRoomWebSocketHandler, "/ws/games/jinzi/rooms/*")
                // 井字棋房间连接，负责落子和房间状态
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
