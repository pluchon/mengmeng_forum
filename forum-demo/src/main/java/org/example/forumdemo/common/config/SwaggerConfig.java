package org.example.forumdemo.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//API界面的工具
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("萌萌论坛测试")          // 文档标题
                .description("论坛项目接口文档")    // 文档描述
                .version("v1.1.0")                // 版本号
                .contact(new Contact().name("Forum Team").email("dev@example.com")));
    }
}
