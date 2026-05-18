package org.example.forumdemo.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.websocket.WebSocketPushService;
import org.example.forumdemo.entity.vo.mq.ArticleAuditResultMqVO;
import org.example.forumdemo.entity.vo.mq.MessageNotifyMqVO;
import org.example.forumdemo.entity.vo.mq.ReplyNotifyMqVO;
import org.example.forumdemo.service.interfaces.article.ArticleService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;

@Slf4j
@Component
public class ForumConsumer {

    @Autowired
    private WebSocketPushService webSocketPushService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * @Lazy 打破 ArticleService 与 ForumConsumer 的潜在循环依赖
     * (ArticleService 内部使用 ForumProducer 投递, 二者间接互相引用)
     */
    @Autowired
    @Lazy
    private ArticleService articleService;

    // 监听帖子回复通知队列，推送实时通知给帖子作者
    @RabbitListener(queues = Constant.QUORUM_QUEUE_1, ackMode = "MANUAL")
    public void handleReplyNotify(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            ReplyNotifyMqVO vo = objectMapper.readValue(message.getBody(), ReplyNotifyMqVO.class);
            log.debug("[MQ 消费者] 收到帖子回复通知 | notifyUserId={} | articleId={}", vo.getNotifyUserId(), vo.getArticleId());
            String pushPayload = objectMapper.writeValueAsString(new HashMap<>() {
                {
                    put("type", "reply");
                    put("articleId", vo.getArticleId());
                    put("fromUser", vo.getPostUsername());
                    put("summary", vo.getContentSummary());
                }
            });
            // 把帖子回复的消息实时推送给我们的作者
            webSocketPushService.push(vo.getNotifyUserId(), pushPayload);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[MQ 消费者] 帖子回复通知失败 | deliveryTag={} | error={}", deliveryTag, e.getMessage(), e);
            // requeue=false：不重回队列，转入死信通道
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // 监听私信通知队列，推送实时通知给接收者
    @RabbitListener(queues = Constant.QUORUM_QUEUE_2, ackMode = "MANUAL")
    public void handleMessageNotify(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            MessageNotifyMqVO vo = objectMapper.readValue(message.getBody(), MessageNotifyMqVO.class);
            log.debug("[MQ 消费者] 收到私信通知 | receiveUserId={} | sendUserId={}", vo.getReceiveUserId(), vo.getSendUserId());
            String pushPayload = objectMapper.writeValueAsString(new java.util.HashMap<>() {
                {
                    put("type", "message");
                    put("dbMessageId", vo.getDbMessageId());
                    // 前端用于匹配当前会话
                    put("fromUserId", vo.getSendUserId());
                    put("fromUser", vo.getSendUsername());
                    put("senderNickname", vo.getSendUsername());
                    put("summary", vo.getContentSummary());
                }
            });
            webSocketPushService.push(vo.getReceiveUserId(), pushPayload);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[MQ 消费者] 私信通知失败 | deliveryTag={} | error={}", deliveryTag, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 监听帖子审核结果队列 q-audit-result, 把 Python LangGraph 的结果应用到帖子状态.
     * payload: ArticleAuditResultMqVO.
     *
     * 幂等保护:
     *   1) Java 侧 Redis SETNX 去重(同一 taskId 只生效一次)
     *   2) ArticleService.applyAuditResult 内部还做了 status/taskId 双重 CAS 校验
     * 异常处理:
     *   - JSON 反序列化失败 -> nack 直接进死信(避免无限重试)
     *   - 业务异常 -> nack 进死信, 由人工排查; 用户侧仍然 PENDING, 由定时任务兜底转 AUDIT_ERROR
     */
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

    // 监听死信队列，归档日志（死信无论如何都 ACK，防止无限循环）
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
