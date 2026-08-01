package org.pluchon.forum.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

// 论坛对外 HTTP 客户端：统一连接/读取超时，避免业务代码散落 new RestTemplate()
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate forumRestTemplate() {
        return build(15_000, 60_000);
    }

    @Bean("aiRestTemplate")
    public RestTemplate aiRestTemplate() {
        return build(15_000, 300_000);
    }

    @Bean("ffmpegRestTemplate")
    public RestTemplate ffmpegRestTemplate() {
        return build(30_000, 1_800_000);
    }

    @Bean("gameAiRestTemplate")
    public RestTemplate gameAiRestTemplate() {
        return build(3_000, 8_000);
    }

    private static RestTemplate build(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
