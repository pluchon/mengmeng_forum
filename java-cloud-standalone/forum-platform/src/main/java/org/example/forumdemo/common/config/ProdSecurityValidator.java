package org.example.forumdemo.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 生产环境启动时校验关键密钥已配置
@Slf4j
@Component
@Profile("prod")
public class ProdSecurityValidator implements ApplicationRunner {

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${pii.secret:}")
    private String piiSecret;

    @Value("${forum.mascot.internal-key:}")
    private String mascotInternalKey;

    @Value("${forum.ai.internal-key:}")
    private String aiInternalKey;

    @Value("${forum.ffmpeg.internal-key:}")
    private String ffmpegInternalKey;

    @Override
    public void run(ApplicationArguments args) {
        assertMinLength("JWT_SECRET", jwtSecret, 32);
        assertMinLength("PII_CRYPTO_SECRET", piiSecret, 32);
        assertMinLength("FORUM_MASCOT_INTERNAL_KEY", mascotInternalKey, 16);
        assertMinLength("FORUM_AI_INTERNAL_KEY", aiInternalKey, 16);
        assertMinLength("FORUM_FFMPEG_INTERNAL_KEY", ffmpegInternalKey, 16);
        log.info("生产环境安全密钥校验通过");
    }

    private static void assertMinLength(String name, String value, int minLen) {
        if (!StringUtils.hasText(value) || value.trim().length() < minLen) {
            throw new IllegalStateException(name + " 未配置或长度不足 " + minLen + "，请在 .env 中设置后再启动");
        }
    }
}
