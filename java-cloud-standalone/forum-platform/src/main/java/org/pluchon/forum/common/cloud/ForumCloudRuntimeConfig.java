package org.pluchon.forum.common.cloud;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(ForumFeaturesProperties.class)
public class ForumCloudRuntimeConfig {

    @Configuration
    @ConditionalOnProperty(name = "forum.features.scheduling", havingValue = "true")
    @EnableScheduling
    static class SchedulingEnableConfig {
    }
}
