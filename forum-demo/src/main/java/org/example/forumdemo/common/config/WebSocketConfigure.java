package org.example.forumdemo.common.config;

import org.example.forumdemo.common.advice.WebSocket;
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

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocket, "/ws/notify")
                // 握手前执行 JWT 鉴权
                .addInterceptors(tokenHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}

