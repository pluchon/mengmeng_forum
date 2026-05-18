package org.example.forumdemo.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
// 单独设置每一个业务逻辑的接口，防止冲突
public class RabbitTemplateConfigure {

    // 统一JSON序列化，消费者收到的 body 是可读的 JSON，便于调试
    // 而且消息队列也是要求进行统一的序列化！
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 帖子回复通知专用 RabbitTemplate
     * 标记 @Primary，作为按类型自动注入时的默认实例
     */
    @Primary
    @Bean("replyRabbitTemplate")
    public RabbitTemplate replyRabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        // Broker 收到消息后触发：ack=true 表示入队成功，false 则需告警或补偿重发
        // 监控消息有没有成功到达交换机，通过ack如果没到达则会进行重发
        template.setConfirmCallback((correlationData, ack, cause) -> {
            String messageId = correlationData != null ? correlationData.getId() : "unknown";
            if (ack) {
                log.debug("[帖子回复MQ] 投递成功 | messageId={}", messageId);
            } else {
                log.error("[帖子回复MQ] 投递失败 | messageId={} | cause={}", messageId, cause);
            }
        });
        //监控消息有没有成功进入队列
        // 消息到达 Exchange 但没有匹配队列时触发
        // RoutingKey 配置错误等情况
        template.setReturnsCallback(returned -> log.error(
                "[帖子回复MQ] 消息被退回 | routingKey={} | replyText={} | body={}",
                returned.getRoutingKey(), returned.getReplyText(),
                new String(returned.getMessage().getBody())));
        return template;
    }

    /**
     * 私信通知专用 RabbitTemplate
     * ConfirmCallback 与帖子回复的日志完全隔离，互不干扰
     */
    @Bean("messageRabbitTemplate")
    public RabbitTemplate messageRabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            String messageId = correlationData != null ? correlationData.getId() : "unknown";
            if (ack) {
                log.debug("[私信MQ] 投递成功 | messageId={}", messageId);
            } else {
                log.error("[私信MQ] 投递失败 | messageId={} | cause={}", messageId, cause);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "[私信MQ] 消息被退回 | routingKey={} | replyText={} | body={}",
                returned.getRoutingKey(), returned.getReplyText(),
                new String(returned.getMessage().getBody())));

        return template;
    }

    /**
     * 帖子异步审核专用 RabbitTemplate
     * 同时承载: Java -> Python 任务下发 (forum.audit.article) + 兜底重投使用
     * 结果回执 (forum.audit.result) 由 Python 发, Java 是消费方, 不需要单独 publisher
     */
    @Bean("auditRabbitTemplate")
    public RabbitTemplate auditRabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            String messageId = correlationData != null ? correlationData.getId() : "unknown";
            if (ack) {
                log.debug("[帖子审核MQ] 投递成功 | messageId={}", messageId);
            } else {
                log.error("[帖子审核MQ] 投递失败 | messageId={} | cause={}", messageId, cause);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "[帖子审核MQ] 消息被退回 | routingKey={} | replyText={} | body={}",
                returned.getRoutingKey(), returned.getReplyText(),
                new String(returned.getMessage().getBody())));
        return template;
    }
}

