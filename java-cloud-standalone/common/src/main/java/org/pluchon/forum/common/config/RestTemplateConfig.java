package org.pluchon.forum.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

// 论坛对外 HTTP 客户端：统一连接/读取超时，避免业务代码散落 new RestTemplate
@Configuration
public class RestTemplateConfig {

    @Value("${forum.ai.timeout.connect-ms:5000}")
    private int aiConnectTimeoutMs;

    @Value("${forum.ai.timeout.fast-ms:25000}")
    private int aiFastTimeoutMs;

    @Value("${forum.ai.timeout.standard-ms:300000}")
    private int aiStandardTimeoutMs;

    @Value("${forum.ai.timeout.long-ms:300000}")
    private int aiLongTimeoutMs;

    @Value("${forum.ai.timeout.index-ms:50000}")
    private int aiIndexTimeoutMs;

    @Value("${forum.ffmpeg.connect-timeout-ms:5000}")
    private int ffmpegConnectTimeoutMs;

    @Value("${forum.ffmpeg.read-timeout-ms:150000}")
    private int ffmpegReadTimeoutMs;

    @Bean
    public RestTemplate forumRestTemplate() {
        return build(5_000, 60_000);
    }

    @Bean("aiFastRestTemplate")
    public RestTemplate aiFastRestTemplate() {
        return build(aiConnectTimeoutMs, aiFastTimeoutMs);
    }

    @Bean({"aiRestTemplate", "aiStandardRestTemplate"})
    public RestTemplate aiStandardRestTemplate() {
        return build(aiConnectTimeoutMs, aiStandardTimeoutMs);
    }

    @Bean("aiLongRestTemplate")
    public RestTemplate aiLongRestTemplate() {
        return build(aiConnectTimeoutMs, aiLongTimeoutMs);
    }

    @Bean("aiIndexRestTemplate")
    public RestTemplate aiIndexRestTemplate() {
        return build(aiConnectTimeoutMs, aiIndexTimeoutMs);
    }

    @Bean("ffmpegRestTemplate")
    public RestTemplate ffmpegRestTemplate() {
        return build(ffmpegConnectTimeoutMs, ffmpegReadTimeoutMs);
    }

    private static RestTemplate build(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
