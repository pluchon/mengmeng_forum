package org.example.forumdemo.service.impl.user;

import org.example.forumdemo.common.constant.Constant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// JWT 版本号：登录、改密、登出后递增，使旧令牌立即失效
@Service
public class JwtTokenVersionService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

    public long nextVersion(Long userId) {
        if (userId == null) {
            return 0L;
        }
        Long next = stringRedisTemplate.opsForValue().increment(Constant.REDIS_KEY_JWT_TOKEN_VERSION + userId);
        return next == null ? currentVersion(userId) : next;
    }

    public void bump(Long userId) {
        if (userId == null) {
            return;
        }
        nextVersion(userId);
    }
}
