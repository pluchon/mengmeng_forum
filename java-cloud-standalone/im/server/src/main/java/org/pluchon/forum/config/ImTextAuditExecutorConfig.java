package org.pluchon.forum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// 私信/群聊文本先发后审线程池
@Configuration
public class ImTextAuditExecutorConfig {

    @Bean(name = "imTextAuditExecutor", destroyMethod = "shutdown")
    public ExecutorService imTextAuditExecutor() {
        return new ThreadPoolExecutor(
                2,
                6,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                r -> {
                    Thread t = new Thread(r, "im-text-audit");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
