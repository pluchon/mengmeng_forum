package org.example.forumdemo.common.mq;

import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class ForumProducer {

    @Autowired
    @Qualifier("replyRabbitTemplate")
    private RabbitTemplate replyRabbitTemplate;

    @Autowired
    @Qualifier("messageRabbitTemplate")
    private RabbitTemplate messageRabbitTemplate;

    @Autowired
    @Qualifier("auditRabbitTemplate")
    private RabbitTemplate auditRabbitTemplate;

    @Autowired
    @Qualifier("gameRabbitTemplate")
    private RabbitTemplate gameRabbitTemplate;

    // 发送帖子回复通知，路由到 q-queue_1
    public void sendReplyNotify(Object message) {
        send(replyRabbitTemplate, Constant.ROUTING_KEY_QUEUE_1, message);
    }

    // 发送私信通知，路由到 q-queue_2
    public void sendMessageNotify(Object message) {
        send(messageRabbitTemplate, Constant.ROUTING_KEY_QUEUE_2, message);
    }

    // 发送帖子异步审核任务，让langgraph进行调用消费
    public void sendArticleAuditTask(Object task) {
        send(auditRabbitTemplate, Constant.ROUTING_KEY_AUDIT_TASK, task);
    }

    // 发送游戏对局结束事件，后续异步处理通知、统计、榜单和棋谱归档
    public void sendGameFinished(Object message) {
        send(gameRabbitTemplate, Constant.ROUTING_KEY_GAME_FINISHED, message);
    }

    // 公共发送，每条消息携带唯一 messageId 便于追踪
    private void send(RabbitTemplate template, String routingKey, Object message) {
        String messageId = UUID.randomUUID().toString();
        CorrelationData correlationData = new CorrelationData(messageId);
        template.convertAndSend(Constant.TOPIC_EXCHANGE_1, routingKey, message, correlationData);
        log.debug("[MQ 生产者] 投递 | routingKey={} | messageId={}", routingKey, messageId);
    }
}
