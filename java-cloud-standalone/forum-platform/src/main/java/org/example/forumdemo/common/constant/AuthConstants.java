package org.example.forumdemo.common.constant;

import java.util.regex.Pattern;

// 认证与会话相关常量
public final class AuthConstants {

    public static final String USER_SESSION = "user_session";

    public static final Pattern VALID_USERNAME_PATTERN = Pattern
            .compile("^[\\u4e00-\\u9fa5a-zA-Z0-9][\\u4e00-\\u9fa5a-zA-Z0-9_-]{2,18}[\\u4e00-\\u9fa5a-zA-Z0-9]$");

    public static final String JWT_NAME = "Authorization";
    public static final String JWT_USER_ID = "userId";
    public static final String JWT_USER_NAME = "username";
    // JWT 版本号，与 Redis forum:jwt:tv:{userId} 对齐；改密/禁言后递增
    public static final String JWT_TOKEN_VERSION = "tv";

    public static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";

    private AuthConstants() {
    }
}
