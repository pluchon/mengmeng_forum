package org.example.forumdemo.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.metrics.ForumMetrics;
import org.example.forumdemo.common.utils.MqEventDedupHelper;
import org.example.forumdemo.service.impl.websocket.WebSocketPushService;
import org.example.forumdemo.entity.vo.mq.ArticleAuditResultMqVO;
import org.example.forumdemo.entity.vo.mq.MessageNotifyMqVO;
import org.example.forumdemo.entity.vo.mq.ReplyNotifyMqVO;
import org.example.forumdemo.service.interfaces.article.ArticleService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "forum.features.mq-consumer", havingValue = "true")
public class ForumConsumer {

    @Autowired
    private WebSocketPushService webSocketPushService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private ArticleService articleService;

    @Autowired
    private MqEventDedupHelper mqEventDedupHelper;

    @Autowired
    private ForumMetrics forumMetrics;

    @RabbitListener(queues = Constant.QUORUM_QUEUE_1, ackMode = "MANUAL")
    public void handleReplyNotify(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            ReplyNotifyMqVO vo = objectMapper.readValue(message.getBody(), ReplyNotifyMqVO.class);
            String eventId = StringUtils.hasText(vo.getMessageId())
                    ? vo.getMessageId()
                    : "reply:" + vo.getArticleId() + ":" + vo.getPostUserId() + ":" + vo.getTimestamp();
            if (!mqEventDedupHelper.tryMarkConsumed(eventId)) {
                log.debug("[MQ 消费者] 帖子回复通知重复 eventId={}", eventId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            log.debug("[MQ 消费者] 收到帖子回复通知 | notifyUserId={} | articleId={}", vo.getNotifyUserId(), vo.getArticleId());
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "reply");
            payload.put("articleId", vo.getArticleId());
            payload.put("fromUser", vo.getPostUsername());
            payload.put("summary", vo.getContentSummary());
            String pushPayload = objectMapper.writeValueAsString(payload);
            webSocketPushService.push(vo.getNotifyUserId(), pushPayload);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            forumMetrics.recordMqConsumeFailure();
            log.error("[MQ 消费者] 帖子回复通知失败 | deliveryTag={} | error={}", deliveryTag, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitListener(queues = Constant.QUORUM_QUEUE_2, ackMode = "MANUAL")
    public void handleMessageNotify(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            MessageNotifyMqVO vo = objectMapper.readValue(message.getBody(), MessageNotifyMqVO.class);
            String eventId = StringUtils.hasText(vo.getMessageId())
                    ? vo.getMessageId()
                    : "msg:" + vo.getDbMessageId();
            if (!mqEventDedupHelper.tryMarkConsumed(eventId)) {
                log.debug("[MQ 消费者] 私信通知重复 eventId={}", eventId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            log.debug("[MQ 消费者] 收到私信通知 | receiveUserId={} | sendUserId={}", vo.getReceiveUserId(), vo.getSendUserId());
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "message");
            payload.put("dbMessageId", vo.getDbMessageId());
            payload.put("fromUserId", vo.getSendUserId());
            payload.put("fromUser", vo.getSendUsername());
            payload.put("senderNickname", vo.getSendUsername());
            payload.put("summary", vo.getContentSummary());
            String pushPayload = objectMapper.writeValueAsString(payload);
            webSocketPushService.push(vo.getReceiveUserId(), pushPayload);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            forumMetrics.recordMqConsumeFailure();
            log.error("[MQ 消费者] 私信通知失败 | deliveryTag={} | error={}", deliveryTag, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitListener(queues = Constant.QUORUM_QUEUE_AUDIT_RESULT, ackMode = "MANUAL")
    public void handleArticleAuditResult(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            ArticleAuditResultMqVO vo = objectMapper.readValue(message.getBody(), ArticleAuditResultMqVO.class);
            log.debug("[MQ 消费者] 收到审核结果 | articleId={} | taskId={} | status={}",
                    vo.getArticleId(), vo.getTaskId(), vo.getFinalStatus());
            articleService.applyAuditResult(vo);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[MQ 消费者] 审核结果处理失败 | deliveryTag={} | error={}", deliveryTag, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitListener(queues = Constant.D_QUORUM_QUEUE_1, ackMode = "MANUAL")
    public void handleDeadLetter(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String originalQueue = (String) message.getMessageProperties().getHeaders().get("x-first-death-queue");
            String deadReason = (String) message.getMessageProperties().getHeaders().get("x-first-death-reason");
            log.warn("[MQ 死信] | 原始队列={} | 原因={} | body={}", originalQueue, deadReason, new String(message.getBody()));
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[MQ 死信] 处理失败 | error={}", e.getMessage(), e);
            channel.basicAck(deliveryTag, false);
        }
    }
}
