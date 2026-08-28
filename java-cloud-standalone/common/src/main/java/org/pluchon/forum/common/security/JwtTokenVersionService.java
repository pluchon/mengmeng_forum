package org.pluchon.forum.common.security;

import org.pluchon.forum.common.constant.Constant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// JWT 最低有效版本号：令牌携带签发时的版本，改密 / 封禁时抬高下限，使已签发的令牌立即失效
//
// 存的是"下限"而不是"当前值"，登录只读不写：
// 若登录也自增，手机登录就会把电脑的令牌顶掉，等于强制单端在线。
//
// 键缺失一律判为无效而不是当成 0：下限归零会让改密码时作废掉的旧令牌重新生效，
// 那是 fail-open。宁可让人重新登录一次，也不能放行本该失效的令牌。
@Service
public class JwtTokenVersionService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 读取最低有效版本；键不存在或内容损坏返回 null，表示"无法确认"
    private Long readVersion(Long userId) {
        if (userId == null) {
            return null;
        }
        String raw = stringRedisTemplate.opsForValue().get(Constant.REDIS_KEY_JWT_TOKEN_VERSION + userId);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 令牌版本不低于下限即有效，因此同一下限下签发的多个令牌可以并存 —— 支持多设备同时在线
    public boolean isValid(Long userId, long jwtVersion) {
        Long floor = readVersion(userId);
        if (floor == null) {
            // Redis 被清空 / 键被淘汰：状态不可信，拒绝并要求重新登录
            return false;
        }
        return jwtVersion >= floor;
    }

    // 登录签发时使用：取当前下限，不自增，避免踢掉该账号其它设备。
    // 顺带确保键存在，这样 isValid 才能用"键缺失"区分出 Redis 丢状态的情况
    public long issueVersion(Long userId) {
        if (userId == null) {
            return 0L;
        }
        String key = Constant.REDIS_KEY_JWT_TOKEN_VERSION + userId;
        stringRedisTemplate.opsForValue().setIfAbsent(key, "0");
        Long floor = readVersion(userId);
        return floor == null ? 0L : floor;
    }

    // 抬高下限，使该账号此前签发的所有令牌立即失效（改密、封禁）
    public void bump(Long userId) {
        if (userId == null) {
            return;
        }
        stringRedisTemplate.opsForValue().increment(Constant.REDIS_KEY_JWT_TOKEN_VERSION + userId);
    }
}
