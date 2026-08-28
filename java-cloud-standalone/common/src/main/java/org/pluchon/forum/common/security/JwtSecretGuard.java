package org.pluchon.forum.common.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.utils.JWTUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

// 启动时校验 JWT 签名密钥
//
// JWTUtils 里留了一个本地开发用的默认密钥，长度刚好过 HS256 的门槛，
// 所以配置缺失时不会报错、会安静地用它签发令牌 —— 而它就写在公开仓库里。
// 一旦生产用上，任何人都能自己签一个令牌登录成任意账号，认证形同虚设。
// 这里在 prod 下把"安静地用默认值"变成"直接起不来"。
@Slf4j
@Component
public class JwtSecretGuard {

    // HS256 要求密钥不短于 256 位
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${jwt.secret:}")
    private String configuredSecret;

    @Autowired
    private Environment environment;

    @PostConstruct
    public void validate() {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        String secret = configuredSecret == null ? "" : configuredSecret.trim();
        boolean missing = !StringUtils.hasText(secret);
        boolean isDevDefault = JWTUtils.DEV_DEFAULT_SECRET.equals(secret);
        boolean tooShort = !missing && secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES;

        if (!missing && !isDevDefault && !tooShort) {
            return;
        }
        String reason = missing ? "未配置 jwt.secret / JWT_SECRET"
                : isDevDefault ? "仍在使用仓库里公开的开发默认密钥"
                : "密钥不足 " + MIN_SECRET_BYTES + " 字节，不满足 HS256 要求";
        if (prod) {
            throw new IllegalStateException("拒绝以不安全的 JWT 密钥启动：" + reason
                    + "。请在 /opt/forum-config/prod.env 配置 JWT_SECRET");
        }
        log.warn("当前 JWT 密钥不可用于生产：{}。本地开发可忽略", reason);
    }
}
