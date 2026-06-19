package org.example.forumdemo.common.interceptor;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.utils.JWTUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

// WebSocket 握手拦截器：在连接建立前验证 JWT，防止伪造 userId
@Slf4j
@Component
public class TokenHandshakeInterceptor implements HandshakeInterceptor {

    // 握手前执行
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 从 URL Query 参数中取 JWT，前端连接格式：ws://host/ws/notify?token=xxx
        // 按参数名匹配，兼容 token 不在第一位的情况（如 ?foo=bar&token=xxx）
        String query = request.getURI().getQuery();
        String token = null;
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    token = param.substring("token=".length());
                    break;
                }
            }
        }
        if (token == null || token.isEmpty()) {
            log.warn("[WS握手] 拒绝：缺少 token 参数 | uri={}", request.getURI());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Claims claims;
        try {
            claims = JWTUtils.parseJWT(token);
        } catch (Exception e) {
            log.warn("[WS握手] 拒绝：token 解析失败 | error={}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (claims == null) {
            log.warn("[WS握手] 拒绝：token 为空或格式非法");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        // 从 JWT 载荷中取出 userId 和 username，存入 attributes
        // 后续 WebSocket 处理器可直接取用
        Long userId = Long.valueOf(claims.get(Constant.JWT_USER_ID).toString());
        String username = (String) claims.get(Constant.JWT_USER_NAME);
        attributes.put(Constant.JWT_USER_ID, userId);
        attributes.put(Constant.JWT_USER_NAME, username);
        log.debug("[WS握手] 鉴权通过 | userId={} | username={}", userId, username);
        return true;
    }

    // 握手完成后回调，目前无需处理
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {}
}
