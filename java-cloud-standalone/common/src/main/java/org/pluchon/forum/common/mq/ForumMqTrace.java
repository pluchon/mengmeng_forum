package org.pluchon.forum.common.mq;

import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * 让 traceId 跨过 RabbitMQ。
 *
 * <p>HTTP 那条链路（网关生成 → TraceIdFilter 落 MDC → Feign 透传 → Java 传给 Python）
 * 一直是通的，唯独消息队列这一段断着：生产者发完就返回，真正干活的是消费者线程，
 * 日志和触发它的那个请求对不上号。帖子审核、评论审核、举报审核全走这条路，
 * 也正是最难查的一段。
 *
 * <p>出站在 {@link ForumProducer} 打头，入站由这里的 afterReceivePostProcessor 兜住：
 * 每条消息进监听器之前先把 traceId 放进 MDC，日志 pattern 里的 %X{traceId} 就有值了。
 */
@Configuration
@ConditionalOnProperty(name = "forum.features.mq", havingValue = "true")
public class ForumMqTrace {

    // 与 HTTP 侧同名，全链路一个 key
    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    /**
     * 覆盖 Boot 自动装配的监听器工厂，只为多挂一个入站处理器。
     * 必须先走 configurer，否则 yml 里的 acknowledge-mode: manual 等配置会全部丢掉——
     * 而所有监听器都是 ackMode = MANUAL，丢了会直接消息重复消费。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAfterReceivePostProcessors(ForumMqTrace::applyTraceId);
        return factory;
    }

    /**
     * 消费线程是复用的，所以这里**每条消息都无条件覆盖**：
     * 消息没带 traceId 就现生成一个，绝不留着上一条的值继续用，
     * 否则两个不相干的任务会顶着同一个 id，比没有还难查。
     */
    private static Message applyTraceId(Message message) {
        Object header = message.getMessageProperties().getHeader(TRACE_HEADER);
        String traceId = header == null ? null : String.valueOf(header).trim();
        if (traceId == null || traceId.isEmpty()) {
            traceId = "mq-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(MDC_KEY, traceId);
        return message;
    }
}
