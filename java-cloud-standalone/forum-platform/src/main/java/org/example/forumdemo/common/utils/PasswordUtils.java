package org.example.forumdemo.common.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;

/**
 * 密码哈希：新用户使用 BCrypt；历史 MD5+盐 在登录成功后渐进迁移。
 */
public final class PasswordUtils {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);

    private PasswordUtils() {
    }

    public static boolean isBcryptHash(String stored) {
        return stored != null && stored.startsWith("$2");
    }

    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String storedHash, String salt) {
        if (!StringUtils.hasLength(rawPassword) || !StringUtils.hasLength(storedHash)) {
            return false;
        }
        if (isBcryptHash(storedHash)) {
            return ENCODER.matches(rawPassword, storedHash);
        }
        if (!StringUtils.hasLength(salt)) {
            return false;
        }
        return MD5Utils.md5SaltHigh(rawPassword, salt).equals(storedHash);
    }
}
