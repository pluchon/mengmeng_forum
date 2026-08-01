package org.example.forumdemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 游戏 AI 落子/思考专用线程池，避免占用 ForkJoinPool.commonPool。
 */
@Configuration
public class GameAiExecutorConfig {

    @Bean(name = "gameAiExecutor")
    public Executor gameAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("game-ai-");
        executor.initialize();
        return executor;
    }
}
