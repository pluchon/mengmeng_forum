package org.example.forumdemo.common.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.StringUtils;

import java.util.Collections;

/**
 * Redis 值原子校验并删除，防止并发下先读后删导致重复消费。
 */
public final class RedisAtomicValueConsumer {

    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "local val = redis.call('GET', KEYS[1]) "
                    + "if not val then return 0 end "
                    + "if val ~= ARGV[1] then return 0 end "
                    + "redis.call('DEL', KEYS[1]) "
                    + "return 1",
            Long.class);

    private RedisAtomicValueConsumer() {
    }

    /**
     * 仅当 Redis 中存储值与 expected 完全一致时删除 key 并返回 true。
     */
    public static boolean consumeIfMatch(StringRedisTemplate redis, String key, String expected) {
        if (redis == null || !StringUtils.hasText(key) || expected == null) {
            return false;
        }
        Long result = redis.execute(CONSUME_SCRIPT, Collections.singletonList(key), expected);
        return result != null && result == 1L;
    }
}
