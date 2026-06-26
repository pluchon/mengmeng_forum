package org.example.forumdemo.service.impl.user;

import org.example.forumdemo.common.constant.Constant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * JWT 版本号：改密 / 禁言后递增，使旧令牌立即失效。
 */
@Service
public class JwtTokenVersionService {

    private final StringRedisTemplate stringRedisTemplate;

    public JwtTokenVersionService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long currentVersion(Long userId) {
        if (userId == null) {
            return 0L;
        }
        String raw = stringRedisTemplate.opsForValue().get(Constant.REDIS_KEY_JWT_TOKEN_VERSION + userId);
        if (!StringUtils.hasText(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public boolean isValid(Long userId, long jwtVersion) {
        return jwtVersion == currentVersion(userId);
    }

    public void bump(Long userId) {
        if (userId == null) {
            return;
        }
        stringRedisTemplate.opsForValue().increment(Constant.REDIS_KEY_JWT_TOKEN_VERSION + userId);
    }
}
