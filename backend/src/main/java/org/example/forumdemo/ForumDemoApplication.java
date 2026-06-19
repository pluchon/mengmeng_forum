package org.example.forumdemo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.env.Environment;

@Slf4j
@EnableScheduling
@SpringBootApplication
public class ForumDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForumDemoApplication.class, args);
    }

    @Bean
    public CommandLineRunner verifyEnv(Environment env) {
        return args -> {
            log.debug("================ 环境配置验证 ================");
            checkEnv(env, "DB_PASSWORD", "数据库密码");
            checkEnv(env, "ALIYUN_ACCESS_KEY_ID", "阿里云 AccessKeyID");
            checkEnv(env, "ALIYUN_ACCESS_KEY_SECRET", "阿里云 AccessKeySecret");
            checkEnv(env, "MAIL_PASSWORD", "邮件服务授权码");
            checkEnv(env, "PII_CRYPTO_SECRET", "敏感信息加密密钥");
            log.debug("============================================");
        };
    }

    private void checkEnv(Environment env, String key, String desc) {
        String value = env.getProperty(key);
        if (value == null) {
            log.warn("⚠️  未读取到环境变量: [{} ({})]，将使用默认配置或启动失败！", key, desc);
        } else {
            String masked = value.length() > 6 
                ? value.substring(0, 3) + "****" + value.substring(value.length() - 3)
                : "****";
            log.debug("已成功读取环境变量: [{} ({})], 当前值: {}", key, desc, masked);
        }
    }
}
