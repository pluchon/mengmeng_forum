package org.example.forum.im;

import org.example.forumdemo.common.utils.ForumDateTimes;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"org.example.forumdemo", "org.example.forum"})
@MapperScan("org.example.forumdemo.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"org.example.forum.api", "org.example.forum.cloud.feign"})
public class ForumImApplication {

    public static void main(String[] args) {
        ForumDateTimes.useShanghaiAsDefault();
        SpringApplication.run(ForumImApplication.class, args);
    }
}
