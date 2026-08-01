package org.example.forumdemo.common.cloud;

import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 注册域 Service 裁剪器（必须是静态 Bean，以便在常规扫描前生效）
@Configuration
public class DomainServicePrunerConfig {

    @Bean
    public static BeanDefinitionRegistryPostProcessor domainServicePruner() {
        return new DomainServicePruner();
    }
}
