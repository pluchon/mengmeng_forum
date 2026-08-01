package org.pluchon.forum.common.interceptor;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.AuthApiPaths;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.utils.JWTUtils;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.common.security.AuthSnapshotResolver;
import org.pluchon.forum.common.security.JwtTokenVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// 登录拦截器：解析 Token 并支持公开路径访问
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    @Lazy
    private AuthSnapshotResolver authSnapshotResolver;

    @Autowired
    private JwtTokenVersionService jwtTokenVersionService;

    //登录前的校验
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 浏览器的预发起请求，此时不会携带令牌，以免直接校验的时候产生的空令牌
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String jwtToken = request.getHeader(Constant.JWT_NAME);
        // 获取我们的请求路径
        String uri = request.getRequestURI();
        Claims jwtClaims = null;
        if (jwtToken != null && !jwtToken.isEmpty()) {
            try {
                jwtClaims = JWTUtils.parseJWT(jwtToken);
            } catch (Exception e) {
                log.warn("令牌解析失败: {}, uri: {}", e.getMessage(), uri);
                // 如果是强制登录路径，解析失败直接拦截
                if (!isOptionalPath(uri, request.getMethod())) {
                    response.setStatus(401);
                    return false;
                }
            }
        }
        if (jwtClaims != null) {
            Long userId = Long.valueOf(jwtClaims.get(Constant.JWT_USER_ID).toString());
            AuthenticatedUser user = authSnapshotResolver.resolve(
                    userId, jwtClaims.get(Constant.JWT_USER_NAME, String.class));
            if (user == null) {
                response.setStatus(401);
                return false;
            }
            if (user.getState() != null && user.getState().equals(Constant.STATE_BANNED)) {
                response.setStatus(403);
                return false;
            }
            long jwtTv = JWTUtils.readTokenVersion(jwtClaims);
            if (!jwtTokenVersionService.isValid(userId, jwtTv)) {
                log.warn("JWT 版本失效 userId={}, uri={}", userId, uri);
                response.setStatus(401);
                return false;
            }
            request.setAttribute(Constant.USER_SESSION, user);
            return true;
        }
        // 到这里说明没有 Token
        // 如果是公开路径（Optional），放行
        if (isOptionalPath(uri, request.getMethod())) {
            return true;
        }
        // 既没有 Token 又不是公开路径，拦截
        response.setStatus(401);
        return false;
    }

    // 检测游客模式下是否是我们的定义好的公开路径，如果不是就进行拦截
    private boolean isOptionalPath(String uri, String method) {
        return AuthApiPaths.isOptionalAuth(uri, method);
    }
}
