package org.example.forumdemo.common.cloud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainControllerPrunerConfig {

    // 必须用 static @Bean，保证在常规业务 Bean 定义完成前完成 Controller 裁剪
    @Bean
    public static DomainControllerPruner domainControllerPruner() {
        return new DomainControllerPruner();
    }
}
