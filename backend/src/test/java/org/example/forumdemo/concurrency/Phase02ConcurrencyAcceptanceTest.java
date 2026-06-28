package org.example.forumdemo.concurrency;

import org.example.forumdemo.common.utils.MqEventDedupHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 02~05 并发验收：MQ 消费幂等、游戏匹配键（需本地 Redis）。
 */
@SpringBootTest
class Phase02ConcurrencyAcceptanceTest {

    @Autowired
    private MqEventDedupHelper mqEventDedupHelper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void mqEventDedup_onlyFirstConsumed() throws InterruptedException {
        String eventId = "test:mq:" + UUID.randomUUID();
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger firstHits = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (mqEventDedupHelper.tryMarkConsumed(eventId)) {
                        firstHits.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        Assertions.assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        Assertions.assertEquals(1, firstHits.get());
    }

    @Test
    void gameMatchKey_setNxBehavior() {
        String key = "forum:game:match:test:" + UUID.randomUUID();
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(key, "room-a", 60, TimeUnit.SECONDS);
        Boolean second = stringRedisTemplate.opsForValue().setIfAbsent(key, "room-b", 60, TimeUnit.SECONDS);
        Assertions.assertTrue(Boolean.TRUE.equals(first));
        Assertions.assertFalse(Boolean.TRUE.equals(second));
        Assertions.assertEquals("room-a", stringRedisTemplate.opsForValue().get(key));
        stringRedisTemplate.delete(key);
    }
}
