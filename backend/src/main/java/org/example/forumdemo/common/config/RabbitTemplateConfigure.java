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
// 单独设置每一个业务逻辑的接口，防止冲突，也是毁了避免在处理回调的时候覆盖掉设置
public class RabbitTemplateConfigure {

    // 统一JSON序列化，消费者收到的 body 是可读的 JSON，便于调试
    // 而且消息队列也是要求进行统一的序列化！而且也是为了跨语言的打通，序列化之后是一个字符串，本质上也是一个可读的JSON
    // 并且内部的内容少，减少了网络带宽的传输消耗
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // 我们的死信队列管的是队列里的消息，对于发送过程未落到队列里是不会管的

    // 帖子回复专用，且为默认配置
    @Primary
    @Bean("replyRabbitTemplate")
    public RabbitTemplate replyRabbitTemplate(ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
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
        // 监控消息有没有成功进入队列
        // 消息到达 Exchange 但没有匹配队列时触发
        // RoutingKey 配置错误等情况
        template.setReturnsCallback(returned -> log.error(
                "[帖子回复MQ] 消息被退回 | routingKey={} | replyText={} | body={}",
                returned.getRoutingKey(), returned.getReplyText(),
                new String(returned.getMessage().getBody())));
        return template;
    }

    // 私信专用
    @Bean("messageRabbitTemplate")
    public RabbitTemplate messageRabbitTemplate(ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
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

    // 帖子异步审核
    // Java 侧向 Python 发送“帖子审核任务”的投递与兜底重投
    @Bean("auditRabbitTemplate")
    public RabbitTemplate auditRabbitTemplate(ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
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
