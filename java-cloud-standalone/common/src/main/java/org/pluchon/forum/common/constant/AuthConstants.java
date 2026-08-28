package org.pluchon.forum.common.constant;

// 认证与会话相关常量
public final class AuthConstants {

    public static final String USER_SESSION = "user_session";

    public static final String JWT_NAME = "Authorization";
    public static final String JWT_USER_ID = "userId";
    public static final String JWT_USER_NAME = "username";
    public static final String JWT_TOKEN_VERSION = "tv";

    public static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";

    private AuthConstants() {
    }
}
