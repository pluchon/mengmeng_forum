package org.pluchon.forum.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** 并发整改可观测性指标 */
@Component
public class ForumMetrics {

    private final Counter idempotencyHits;
    private final Counter mqConsumeFailures;
    private final Counter aiCallFailures;
    private final Timer aiCallLatency;
    private final Timer hotRankingRebuild;

    public ForumMetrics(MeterRegistry registry) {
        this.idempotencyHits = Counter.builder("forum.idempotency.hits")
                .description("幂等命中次数（重复请求被短路）")
                .register(registry);
        this.mqConsumeFailures = Counter.builder("forum.mq.consume.failures")
                .description("MQ 消费失败次数")
                .register(registry);
        this.aiCallFailures = Counter.builder("forum.ai.call.failures")
                .description("AI 调用失败/超时/断开次数")
                .register(registry);
        this.aiCallLatency = Timer.builder("forum.ai.call.latency")
                .description("AI 调用耗时")
                .register(registry);
        this.hotRankingRebuild = Timer.builder("forum.hot_ranking.rebuild")
                .description("热帖榜重算耗时")
                .register(registry);
    }

    public void recordIdempotencyHit() {
        idempotencyHits.increment();
    }

    public void recordMqConsumeFailure() {
        mqConsumeFailures.increment();
    }

    public void recordAiCallFailure() {
        aiCallFailures.increment();
    }

    public void recordAiCallLatency(long millis) {
        aiCallLatency.record(millis, TimeUnit.MILLISECONDS);
    }

    public Timer.Sample startHotRankingRebuild() {
        return Timer.start();
    }

    public void recordHotRankingRebuild(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(hotRankingRebuild);
        }
    }
}
