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
                // 绝不能用 CallerRunsPolicy：调用方是 afterCommit 里的 Tomcat 请求线程，
                // 一次 ffmpeg 转码可能几十分钟，队列一满就会把工作线程一个个占死。
                // 这里直接拒绝，帖子保持 PROCESSING，交给转码兜底任务重新入队
                new ThreadPoolExecutor.AbortPolicy());
    }
}
