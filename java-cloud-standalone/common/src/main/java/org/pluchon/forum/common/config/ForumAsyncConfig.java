package org.pluchon.forum.common.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

// SSE 等长连接异步任务：使用 Spring 托管线程池，支持优雅停机
@Configuration
public class ForumAsyncConfig {

    @Bean("sseExecutor")
    public Executor sseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // SSE 是长任务：一条流会独占线程直到结束（最长 180 秒）。
        // ThreadPoolTaskExecutor 是「先占满 core、再进队列、队列满了才扩到 max」，
        // 原来 core=4 queue=64，等于永远只有 4 个线程——第 5 个人发消息会静默
        // 排在队列里，一个 SSE 事件都收不到，直到前面有人结束或他自己超时。
        // 这里把 core 提上来、队列压到 0，让它先扩线程再排队。
        executor.setCorePoolSize(32);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("forum-sse-");
        // 池满时在调用线程上跑，总比把请求丢了强
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 长时间没人聊时把多余线程回收掉
        executor.setKeepAliveSeconds(120);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setTaskDecorator(this::withTraceContext);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("recommendationExecutor")
    public Executor recommendationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("forum-recommendation-");
        executor.setTaskDecorator(this::withTraceContext);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }

    private Runnable withTraceContext(Runnable task) {
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
    }
}
