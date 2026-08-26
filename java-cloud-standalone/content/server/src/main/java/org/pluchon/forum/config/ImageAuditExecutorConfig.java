package org.pluchon.forum.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// 图片上传/审图专用线程池，勿复用 SSE/推荐池
@Configuration
public class ImageAuditExecutorConfig {

    @Value("${forum.image-audit.pool-size:4}")
    private int poolSize;

    @Bean(destroyMethod = "shutdown")
    public ExecutorService imageAuditExecutor() {
        int size = Math.max(2, Math.min(poolSize, 8));
        AtomicInteger seq = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                size,
                size,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("image-audit-" + seq.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
