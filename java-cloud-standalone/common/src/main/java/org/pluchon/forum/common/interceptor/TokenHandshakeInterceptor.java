package org.pluchon.forum.common.interceptor;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.utils.JWTUtils;
import org.pluchon.forum.common.security.JwtTokenVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
public class TokenHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtTokenVersionService jwtTokenVersionService;

    // 握手前执行
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   @NonNull Map<String, Object> attributes) {
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
        Long userId;
        String username;
        try {
            userId = Long.valueOf(claims.get(Constant.JWT_USER_ID).toString());
            username = (String) claims.get(Constant.JWT_USER_NAME);
        } catch (Exception e) {
            log.warn("[WS握手] 拒绝：token 载荷缺少用户身份 | error={}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        long jwtTv = JWTUtils.readTokenVersion(claims);
        if (!jwtTokenVersionService.isValid(userId, jwtTv)) {
            log.warn("[WS握手] 拒绝：JWT 版本失效 | userId={}", userId);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(Constant.JWT_USER_ID, userId);
        attributes.put(Constant.JWT_USER_NAME, username);
        log.debug("[WS握手] 鉴权通过 | userId={} | username={}", userId, username);
        return true;
    }

    // 握手完成后回调，目前无需处理
    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
                               @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, Exception exception) {}
}
