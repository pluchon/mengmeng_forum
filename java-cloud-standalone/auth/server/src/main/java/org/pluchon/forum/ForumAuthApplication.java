package org.pluchon.forum;

import org.pluchon.forum.common.utils.ForumDateTimes;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "org.pluchon.forum")
@MapperScan("org.pluchon.forum.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {
        "org.pluchon.forum.api",
        "org.pluchon.forum.auth.client",
        "org.pluchon.forum.cloud.feign",
        "org.pluchon.forum.auth.client"
})
public class ForumAuthApplication {

    public static void main(String[] args) {
        ForumDateTimes.useShanghaiAsDefault();
        SpringApplication.run(ForumAuthApplication.class, args);
    }
}
