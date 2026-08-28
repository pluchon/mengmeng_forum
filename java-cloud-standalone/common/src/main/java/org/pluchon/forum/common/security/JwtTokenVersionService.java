package org.pluchon.forum.common.security;

import org.pluchon.forum.common.constant.Constant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// JWT 最低有效版本号：令牌携带签发时的版本，改密 / 登出时抬高下限，使已签发的令牌立即失效
//
// 这里存的是"下限"而不是"当前值"，登录只读不写：
// 若登录也自增，手机登录就会把电脑的令牌顶掉，等于强制单端在线
@Service
public class JwtTokenVersionService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 当前最低有效版本，未设置视为 0
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

    // 令牌版本不低于下限即有效，因此同一下限下签发的多个令牌可以并存 —— 支持多设备同时在线
    public boolean isValid(Long userId, long jwtVersion) {
        return jwtVersion >= currentVersion(userId);
    }

    // 登录签发时使用：取当前下限，不自增，避免踢掉该账号其它设备
    public long issueVersion(Long userId) {
        return currentVersion(userId);
    }

    // 抬高下限，使该账号此前签发的所有令牌立即失效（改密、登出、封禁）
    public void bump(Long userId) {
        if (userId == null) {
            return;
        }
        stringRedisTemplate.opsForValue().increment(Constant.REDIS_KEY_JWT_TOKEN_VERSION + userId);
    }
}
