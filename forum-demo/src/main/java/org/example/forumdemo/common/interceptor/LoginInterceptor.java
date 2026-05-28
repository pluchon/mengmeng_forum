package org.example.forumdemo.common.interceptor;

import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.utils.JWTUtils;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.mapper.UserMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * 登录拦截器：解析 Token 并支持公开路径访问
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private UserMapper userMapper;

    // 不需要强制登录也能解析 Token 的公开 API 路径（与 Configurer 中的部分排除逻辑对应）
    private static final List<String> OPTIONAL_PATHS = Arrays.asList(
            "/article/selectArticleDetailByArticleId",
            "/article/getHotArticleList",
            "/user/findPasswordByMail",
            "/user/findPasswordBySms",
            "/reply/select",
            "/category/getCategoryWithBoards",
            "/category/articles",
            "/board/topBoardList",
            "/board/selectBoardBy",
            "/board/selectBoardListByBoardIdWithPage",
            "/shop/list",
            "/shop/detail",
            "/favorite/folder/userList",
            "/search/article",
            "/search/user",
            // 公告中心：已发布公告列表（直查库，前端每次打开弹窗请求）
            "/notice/center/list",
            "/mascot/public/"
    );

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
                if (!isOptionalPath(uri)) {
                    response.setStatus(401);
                    return false;
                }
            }
        }
        if (jwtClaims != null) {
            User user = new User();
            Long userId = Long.valueOf(jwtClaims.get(Constant.JWT_USER_ID).toString());
            user.setId(userId);
            user.setUsername(jwtClaims.get(Constant.JWT_USER_NAME, String.class));
            // VIP / 管理员 的判断校验
            try {
                User dbUser = userMapper.selectById(userId);
                if (dbUser != null) {
                    user.setVipTier(dbUser.getVipTier());
                    user.setVipExpireAt(dbUser.getVipExpireAt());
                    user.setIsAdmin(dbUser.getIsAdmin());
                    user.setState(dbUser.getState());
                }
            } catch (Exception e) {
                log.warn("拦截器补全用户字段失败 userId={}, uri={}, err={}", userId, uri, e.getMessage());
            }
            request.setAttribute(Constant.USER_SESSION, user);
            return true;
        }
        // 到这里说明没有 Token
        // 如果是公开路径（Optional），放行
        if (isOptionalPath(uri)) {
            return true;
        }
        // 既没有 Token 又不是公开路径，拦截
        response.setStatus(401);
        return false;
    }

    // 检测游客模式下是否是我们的定义好的公开路径，如果不是就进行拦截
    private boolean isOptionalPath(String uri) {
        return OPTIONAL_PATHS.stream().anyMatch(uri::startsWith);
    }
}
