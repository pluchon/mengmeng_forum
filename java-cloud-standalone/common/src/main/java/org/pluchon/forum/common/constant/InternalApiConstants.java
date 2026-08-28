package org.pluchon.forum.common.constant;

// Java 服务间 internal 接口约定
public final class InternalApiConstants {

    // 与 Java→Python 共用头名，但密钥使用 FORUM_SERVICE_INTERNAL_KEY
    public static final String INTERNAL_KEY_HEADER = "X-Internal-Key";

    private static final String INTERNAL_PATH_MARKER = "/internal/";

    private InternalApiConstants() {
    }

    public static boolean isInternalPath(String path) {
        return path != null && path.contains(INTERNAL_PATH_MARKER);
    }
}
