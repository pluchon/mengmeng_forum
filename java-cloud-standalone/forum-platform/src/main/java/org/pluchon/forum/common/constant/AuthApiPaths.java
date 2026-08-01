package org.pluchon.forum.common.constant;

import java.util.Set;

// 登录拦截器的游客与预登录接口白名单
public final class AuthApiPaths {

    // 游客可读接口：精确匹配 GET，携带 Token 时仍会解析并注入 USER_SESSION。
    private static final Set<String> OPTIONAL_GET_PATHS = Set.of(
            "/article/selectArticleDetailByArticleId",
            "/article/getArticleListByUserIdWithPageAndUserInfo",
            "/article/getHotArticleList",
            "/article/getHotArticleListWithPage",
            "/articleQuestion/acceptedAnswer",
            "/articleReply/getArticleReplyByArticleIdWithPage",
            "/articleSubReply/getSubReplyByReplyId",
            "/category/getCategoryWithBoards",
            "/category/articles",
            "/article/tag/list",
            "/article/tag/suggest",
            "/board/topBoardList",
            "/board/selectBoardByBoardId",
            "/board/selectBoardBy",
            "/board/selectBoardListByBoardIdWithPage",
            "/recommend/feed",
            "/shop/list",
            "/shop/detail",
            "/favorite/folder/userList",
            "/like/queryArticleListForUserLikeWithPage",
            "/user/followStats",
            "/user/followingList",
            "/user/followerList",
            "/search/article",
            "/search/user",
            "/articleDanmaku/listByTimeWindow",
            "/notice/center/list",
            "/mascot/public/models"
    );

    // 无登录态即可发起、但依赖验证码票据或验证码本身保护的预登录接口。
    private static final Set<String> OPTIONAL_POST_PATHS = Set.of(
            "/user/login",
            "/user/register",
            "/user/findPasswordByMail",
            "/user/findPasswordBySms",
            "/mail/login",
            "/sms/login",
            "/captcha/generate",
            "/captcha/check"
    );

    private AuthApiPaths() {
    }

    public static boolean isOptionalAuth(String uri, String method) {
        if (uri == null || uri.isEmpty() || method == null || method.isEmpty()) {
            return false;
        }
        if ("GET".equalsIgnoreCase(method)) {
            return OPTIONAL_GET_PATHS.contains(uri);
        }
        return "POST".equalsIgnoreCase(method) && OPTIONAL_POST_PATHS.contains(uri);
    }

    /** WebMvc 注册用：静态资源、文档和 WebSocket 由各自机制处理。 */
    public static String[] interceptorExcludePatterns() {
        return new String[]{
                "/*.html",
                "/swagger*/**",
                "/swagger-ui/**",
                "/v3/**",
                "/dist/**",
                "/css/**",
                "/image/**",
                "/js/**",
                "/**.ico",
                "/ws/**",
                "/user/internal/**",
                "/points/internal/**",
                "/growth/internal/**",
                "/vip/internal/**",
                "/shop/internal/**",
                "/mascot/internal/**",
                "/ai/internal/**",
                "/system-message/internal/**",
                "/favorite/internal/**",
                "/file/internal/**",
                "/article/internal/**",
                "/actuator/**"
        };
    }
}
