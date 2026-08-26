package org.pluchon.forum.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

// 游戏 AI 落子/思考专用线程池，避免占用 ForkJoinPool.commonPool
@Configuration
public class GameAiExecutorConfig {

    @Value("${forum.game.ai.core-pool-size:2}")
    private int corePoolSize;

    @Value("${forum.game.ai.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${forum.game.ai.queue-capacity:16}")
    private int queueCapacity;

    @Bean(name = "gameAiExecutor")
    public Executor gameAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("game-ai-");
        executor.setTaskDecorator(task -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
