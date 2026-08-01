package org.pluchon.forum.im;

import org.pluchon.forum.common.utils.ForumDateTimes;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "org.pluchon.forum")
@MapperScan("org.pluchon.forum.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"org.pluchon.forum.api", "org.pluchon.forum.cloud.feign"})
public class ForumImApplication {

    public static void main(String[] args) {
        ForumDateTimes.useShanghaiAsDefault();
        SpringApplication.run(ForumImApplication.class, args);
    }
}
