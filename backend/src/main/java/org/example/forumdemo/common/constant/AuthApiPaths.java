package org.example.forumdemo.common.constant;

import java.util.List;

// 登录拦截器与 WebMvc 排除路径的单一配置源
public final class AuthApiPaths {

    // 完全不经过 LoginInterceptor 的路径前缀（与 AppInterceptorConfigurer 排除列表对齐）
    public static final List<String> INTERCEPTOR_EXCLUDED_PREFIXES = List.of(
            "/user/login",
            "/user/register",
            "/captcha/",
            "/notice/center/",
            "/mail/send",
            "/mail/login",
            "/sms/send",
            "/sms/login",
            "/swagger",
            "/swagger-ui/",
            "/v3/",
            "/dist/",
            "/css/",
            "/image/",
            "/js/",
            "/ws/"
    );

    // 游客可访问、但若携带 Token 仍会解析并注入 USER_SESSION 的 API 前缀
    public static final List<String> OPTIONAL_AUTH_PREFIXES = List.of(
            "/article/selectArticleDetailByArticleId",
            "/article/getHotArticleList",
            "/articleQuestion/acceptedAnswer",
            "/user/findPasswordByMail",
            "/user/findPasswordBySms",
            "/reply/select",
            "/category/getCategoryWithBoards",
            "/category/articles",
            "/article/tag/list",
            "/article/tag/suggest",
            "/board/topBoardList",
            "/board/selectBoardBy",
            "/board/selectBoardListByBoardIdWithPage",
            "/recommend/feed",
            "/shop/list",
            "/shop/detail",
            "/favorite/folder/userList",
            "/user/followStats",
            "/user/followingList",
            "/user/followerList",
            "/search/article",
            "/search/user",
            "/articleDanmaku/listByTimeWindow",
            "/notice/center/list",
            "/mascot/public/"
    );

    private AuthApiPaths() {
    }

    public static boolean isInterceptorExcluded(String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        if (uri.endsWith(".html") || uri.endsWith(".ico")) {
            return true;
        }
        return INTERCEPTOR_EXCLUDED_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    public static boolean isOptionalAuth(String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        return OPTIONAL_AUTH_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    /** WebMvc 注册用：完全不经过 LoginInterceptor 的路径 */
    public static String[] interceptorExcludePatterns() {
        return new String[]{
                "/*.html",
                "/user/login",
                "/user/register",
                "/captcha/**",
                "/notice/center/**",
                "/mail/send*",
                "/mail/login",
                "/sms/send*",
                "/sms/login",
                "/swagger*/**",
                "/swagger-ui/**",
                "/v3/**",
                "/dist/**",
                "/css/**",
                "/image/**",
                "/js/**",
                "/**.ico",
                "/ws/**"
        };
    }
}
