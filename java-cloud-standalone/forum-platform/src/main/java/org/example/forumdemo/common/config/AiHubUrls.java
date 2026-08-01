package org.example.forumdemo.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Python ai-server 基址（与 {@code forum.ai.hub-base-url} / {@code FORUM_AI_HUB_BASE_URL} 一致）。
 * 供同步工具统一调用 AI Gateway。
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
        log.info("AI Gateway base URL: {}", baseUrl);
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

    public static String gatewayInvokeUrl() {
        return path("/api/v1/gateway/invoke");
    }
}
