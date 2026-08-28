package org.pluchon.forum.common.constant;

import java.util.Set;
import java.util.regex.Pattern;

// 登录拦截器的游客与预登录接口白名单
public final class AuthApiPaths {

    // 游客可读接口
    private static final Set<String> OPTIONAL_GET_PATHS = Set.of(
            "/article/selectArticleDetailByArticleId",
            "/article/summary",
            "/article/getArticleListByUserIdWithPageAndUserInfo",
            "/article/getHotArticleListWithPage",
            "/articleReply/getArticleReplyByArticleIdWithPage",
            "/articleSubReply/getSubReplyByReplyId",
            "/category/getCategoryWithBoards",
            "/category/articles",
            "/article/tag/list",
            "/board/topBoardList",
            "/board/selectBoardListByBoardIdWithPage",
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
            // 氛围标签候选集，只有几个配置词、无业务数据；
            // ai-server 无凭据回调这里取同一份候选集，必须对游客放行
            "/article/music/moods"
    );

    // 公开收藏夹的夹内帖子，路径带 folderId 无法用等值匹配；私密夹由 FavoriteService 按 -1 过滤
    private static final Pattern OPTIONAL_GET_PATTERNS =
            Pattern.compile("^/favorite/folder/\\d+/articles$");

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
            return OPTIONAL_GET_PATHS.contains(uri) || OPTIONAL_GET_PATTERNS.matcher(uri).matches();
        }
        return "POST".equalsIgnoreCase(method) && OPTIONAL_POST_PATHS.contains(uri);
    }

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
                "/vip/internal/**",
                "/shop/internal/**",
                "/mascot/internal/**",
                "/ai/internal/**",
                "/system-message/internal/**",
                "/websocket/internal/**",
                "/favorite/internal/**",
                "/file/internal/**",
                "/article/internal/**",
                "/actuator/**"
        };
    }
}
