package org.pluchon.forum.content.client.config;

import feign.Request;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

// 内容服务调用 AI 域的专用超时配置
public class ContentAiFeignConfiguration {

    // 与 forum.ai.timeout.long-ms 对齐；硬编码秒数会盖住 Nacos，导致先超时再变成 500
    @Bean
    public Request.Options contentAiRequestOptions(
            @Value("${forum.ai.timeout.connect-ms:5000}") int connectTimeoutMs,
            @Value("${forum.ai.timeout.long-ms:300000}") int readTimeoutMs) {
        return new Request.Options(
                Duration.ofMillis(Math.max(1000, connectTimeoutMs)),
                Duration.ofMillis(Math.max(30_000, readTimeoutMs)),
                true);
    }

    // 将入口链路编号传递到 AI 服务，便于跨服务定位问题
    @Bean
    public RequestInterceptor contentAiTraceInterceptor() {
        return template -> {
            String traceId = MDC.get("traceId");
            if (traceId != null && !traceId.isBlank()) {
                template.header("X-Trace-Id", traceId);
            }
        };
    }
}
