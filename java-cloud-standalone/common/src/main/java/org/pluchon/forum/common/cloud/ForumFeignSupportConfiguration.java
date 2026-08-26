package org.pluchon.forum.common.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.pluchon.forum.common.constant.InternalApiConstants;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

// OpenFeign 官方支持的 RequestInterceptor / ErrorDecoder
@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class ForumFeignSupportConfiguration {

    @Bean
    public RequestInterceptor forumInternalAndTraceRequestInterceptor(
            @Value("${forum.service.internal-key:}") String serviceInternalKey) {
        return template -> {
            String traceId = MDC.get("traceId");
            if (StringUtils.hasText(traceId)) {
                template.header("X-Trace-Id", traceId);
            }
            String url = template.url();
            if (InternalApiConstants.isInternalPath(url) && StringUtils.hasText(serviceInternalKey)) {
                template.header(InternalApiConstants.INTERNAL_KEY_HEADER, serviceInternalKey);
            }
        };
    }

    @Bean
    public ErrorDecoder forumFeignErrorDecoder(ObjectMapper objectMapper) {
        return new ForumFeignErrorDecoder(objectMapper);
    }
}
