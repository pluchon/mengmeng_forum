package org.example.forumdemo.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Python ai-server 基址（与 {@code forum.ai.hub-base-url} / {@code FORUM_AI_HUB_BASE_URL} 一致）。
 * 供 {@link org.example.forumdemo.common.utils.AiAuditUtils} 等同步 HTTP 调用使用。
 */
@Slf4j
@Component
public class AiHubUrls {

    private static volatile String baseUrl = "http://localhost:5000";

    @Value("${forum.ai.hub-base-url:http://localhost:5000}")
    private String hubBaseUrl;

    @PostConstruct
    void init() {
        baseUrl = normalize(hubBaseUrl);
        log.info("AI hub base URL for audit/RAG: {}", baseUrl);
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "http://localhost:5000";
        }
        String t = raw.trim();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }

    private static String path(String suffix) {
        return baseUrl + suffix;
    }

    public static String validateImageUrl() {
        return path("/api/v1/validate-image");
    }

    public static String validateTextUrl() {
        return path("/api/v1/validate-text");
    }

    public static String summarizeUrl() {
        return path("/api/v1/summarize");
    }

    public static String summarizeStreamUrl() {
        return path("/api/v1/summarize/stream");
    }

    public static String articleRagSearchUrl() {
        return path("/api/v1/article-rag-search");
    }

    public static String userRagSearchUrl() {
        return path("/api/v1/user-rag-search");
    }

    public static String gobangMoveUrl() {
        return path("/api/v1/ai/gobang-move");
    }

    public static String jinziMoveUrl() {
        return path("/api/v1/ai/jinzi-move");
    }
}
