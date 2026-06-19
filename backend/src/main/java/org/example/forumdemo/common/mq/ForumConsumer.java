package org.example.forumdemo.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.service.impl.websocket.WebSocketPushService;
import org.example.forumdemo.entity.vo.mq.ArticleAuditResultMqVO;
import org.example.forumdemo.entity.vo.mq.GameFinishedMqVO;
import org.example.forumdemo.entity.vo.mq.MessageNotifyMqVO;
import org.example.forumdemo.entity.vo.mq.ReplyNotifyMqVO;
import org.example.forumdemo.service.interfaces.article.ArticleService;
import org.example.forumdemo.service.interfaces.game.GameMqEventService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ForumConsumer {

    @Autowired
    private WebSocketPushService webSocketPushService;

    @Autowired
    private ObjectMapper objectMapper;

    // lazy 打破循环依赖，因为我们和帖子服务存在循环依赖关系
    // 只有在真正调用的时候才去加载对应的实例
    @Autowired
    @Lazy
    private ArticleService articleService;

    @Autowired
    private GameMqEventService gameMqEventService;

    // 我们的ackMode是手动进行确认的模式，不是自动确认

    // 监听帖子回复通知队列，推送实时通知给帖子作者
    @RabbitListener(queues = Constant.QUORUM_QUEUE_1, ackMode = "MANUAL")
    public void handleReplyNotify(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 反序列化为我们想要的对象
            ReplyNotifyMqVO vo = objectMapper.readValue(message.getBody(), ReplyNotifyMqVO.class);
            log.debug("[MQ 消费者] 收到帖子回复通知 | notifyUserId={} | articleId={}", vo.getNotifyUserId(), vo.getArticleId());
            // 写入载荷推送给 websocket
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "reply");
            payload.put("articleId", vo.getArticleId());
            payload.put("fromUser", vo.getPostUsername());
            payload.put("summary", vo.getContentSummary());
            String pushPayload = objectMapper.writeValueAsString(payload);
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
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "message");
            payload.put("dbMessageId", vo.getDbMessageId());
            // 前端用于匹配当前会话
            payload.put("fromUserId", vo.getSendUserId());
            payload.put("fromUser", vo.getSendUsername());
            payload.put("senderNickname", vo.getSendUsername());
            payload.put("summary", vo.getContentSummary());
            String pushPayload = objectMapper.writeValueAsString(payload);
            webSocketPushService.push(vo.getReceiveUserId(), pushPayload);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[MQ 消费者] 私信通知失败 | deliveryTag={} | error={}", deliveryTag, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // 监听帖子审核结果
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

    // 监听游戏对局结束事件，当前先完成消费幂等骨架，后续接入通知、统计和榜单刷新
    @RabbitListener(queues = Constant.QUORUM_QUEUE_GAME_FINISHED, ackMode = "MANUAL")
    public void handleGameFinished(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            GameFinishedMqVO vo = objectMapper.readValue(message.getBody(), GameFinishedMqVO.class);
            log.debug("[MQ 消费者] 收到游戏结束事件 | gameCode={} | roomId={} | eventId={}",
                    vo.getGameCode(), vo.getRoomId(), vo.getEventId());
            gameMqEventService.handleGameFinished(vo);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[MQ 消费者] 游戏结束事件处理失败 | deliveryTag={} | error={}", deliveryTag, e.getMessage(), e);
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
