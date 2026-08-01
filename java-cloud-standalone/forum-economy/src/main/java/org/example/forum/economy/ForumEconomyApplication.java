package org.example.forum.economy;

import org.example.forumdemo.common.utils.ForumDateTimes;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "org.example.forumdemo")
@MapperScan("org.example.forumdemo.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"org.example.forum.api", "org.example.forumdemo.cloud.feign"})
public class ForumEconomyApplication {

    public static void main(String[] args) {
        ForumDateTimes.useShanghaiAsDefault();
        SpringApplication.run(ForumEconomyApplication.class, args);
    }
}
