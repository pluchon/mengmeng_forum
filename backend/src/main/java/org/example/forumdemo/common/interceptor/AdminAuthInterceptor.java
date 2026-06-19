package org.example.forumdemo.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.entity.db.User;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// 管理员拦截器
// 我们的JWT令牌是经过重新签发更新的
@Slf4j
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        User u = (User) request.getAttribute(Constant.USER_SESSION);
        if (u == null) {
            response.setStatus(401);
            return false;
        }
        if (u.getIsAdmin() == null || u.getIsAdmin() != 1) {
            log.warn("非管理员访问后台 uri={}, userId={}", request.getRequestURI(), u.getId());
            response.setStatus(403);
            return false;
        }
        return true;
    }
}
