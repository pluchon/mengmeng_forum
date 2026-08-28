package org.pluchon.forum.common.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

// 固定窗口计数：首次自增时设置 TTL，超过上限返回 false
// 用于按 IP / 手机号 等维度做轻量防刷，不引入额外限流框架
public final class RedisWindowCounter {

    private RedisWindowCounter() {
    }

    // key 为空时视为无法计数直接放行，避免取不到 IP 时误伤正常用户
    public static boolean tryAcquire(StringRedisTemplate redis, String key, int max, long ttlSeconds) {
        if (redis == null || !StringUtils.hasText(key)) {
            return true;
        }
        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            return true;
        }
        if (count == 1L) {
            redis.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
        return count <= max;
    }
}
