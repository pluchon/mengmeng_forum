package org.example.forumdemo.common.config;

import org.example.forumdemo.common.advice.WebSocket;
import org.example.forumdemo.common.interceptor.TokenHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

// IM 通知 WebSocket（/ws/notify）
@Configuration
@EnableWebSocket
@ConditionalOnExpression("'true'.equals('${forum.features.websocket:false}') && 'im'.equals('${forum.domain:}')")
public class ImWebSocketConfigure implements WebSocketConfigurer {

    @Autowired
    private WebSocket webSocket;

    @Autowired
    private TokenHandshakeInterceptor tokenHandshakeInterceptor;

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
    }
}
