package org.pluchon.forum.common.utils;

import org.pluchon.forum.common.constant.ForumRedisKeys;
import org.pluchon.forum.common.metrics.ForumMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * MQ 消费幂等：同一 eventId 在 TTL 内只处理一次。
 */
@Component
public class MqEventDedupHelper {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ForumMetrics forumMetrics;

    /**
     * @return true 表示首次消费，false 表示重复消息应跳过
     */
    public boolean tryMarkConsumed(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            return true;
        }
        String key = ForumRedisKeys.MQ_EVENT_DEDUP + eventId.trim();
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                key, "1", ForumRedisKeys.TTL_MQ_EVENT_DEDUP, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first)) {
            forumMetrics.recordIdempotencyHit();
        }
        return Boolean.TRUE.equals(first);
    }
}
