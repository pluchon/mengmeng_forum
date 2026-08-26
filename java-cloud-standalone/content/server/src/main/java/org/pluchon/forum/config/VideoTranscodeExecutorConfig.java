package org.pluchon.forum.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// 视频 HLS 异步转码线程池
@Configuration
public class VideoTranscodeExecutorConfig {

    @Value("${forum.video-transcode.pool-size:2}")
    private int poolSize;

    @Bean(destroyMethod = "shutdown")
    public ExecutorService videoTranscodeExecutor() {
        int size = Math.max(1, Math.min(poolSize, 4));
        AtomicInteger seq = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                size,
                size,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(32),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("video-hls-" + seq.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
