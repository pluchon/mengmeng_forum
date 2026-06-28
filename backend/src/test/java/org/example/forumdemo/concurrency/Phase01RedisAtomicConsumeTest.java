package org.example.forumdemo.concurrency;

import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.utils.RedisAtomicValueConsumer;
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
 * Phase 01 P0 并发验收测试（需本地 MySQL + Redis 可用）。
 * 点赞/评论/VIP/积分/抽奖等全链路测试可在联调环境按文档 20 并发标准扩展。
 */
@SpringBootTest
class Phase01RedisAtomicConsumeTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void captchaTicketStyleConsume_onlyOneSuccess() throws InterruptedException {
        String key = "forum:test:atomic:" + UUID.randomUUID();
        String expected = "REGISTER";
        stringRedisTemplate.opsForValue().set(key, expected);

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (RedisAtomicValueConsumer.consumeIfMatch(stringRedisTemplate, key, expected)) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        Assertions.assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        Assertions.assertEquals(1, success.get(), "同一票据并发消费只能成功 1 次");
        stringRedisTemplate.delete(key);
    }
}
