package org.example.forumdemo.service.impl.user;

import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.entity.db.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Map;

// UserAuthSnapshot 本地/远程实现共用的 Redis 缓存补全逻辑
public final class UserAuthSnapshotSupport {

    private UserAuthSnapshotSupport() {
    }

    public static boolean applyFromRedisCache(User user, StringRedisTemplate stringRedisTemplate) {
        String cacheKey = Constant.REDIS_KEY_USER_INFO + user.getId();
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(cacheKey);
        if (map.isEmpty()
                || !map.containsKey("vipTier")
                || !map.containsKey("state")
                || !map.containsKey("creatorState")) {
            return false;
        }
        user.setVipTier(parseByte(map.get("vipTier"), (byte) 0));
        user.setIsAdmin(parseByte(map.get("isAdmin"), (byte) 0));
        user.setState(parseByte(map.get("state"), (byte) 0));
        user.setCreatorState(parseByte(map.get("creatorState"), (byte) 0));
        String vipExpireMs = map.getOrDefault("vipExpireMs", "").toString();
        if (StringUtils.hasText(vipExpireMs)) {
            user.setVipExpireAt(new Date(Long.parseLong(vipExpireMs.trim())));
        }
        return true;
    }

    public static Byte parseByte(Object raw, byte defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Byte.valueOf(raw.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
