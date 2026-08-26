package org.pluchon.forum.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.utils.MqEventDedupHelper;
import org.pluchon.forum.service.interfaces.message.ChatMessageReportService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

// IM域AI异步审核结果消费者
@Slf4j
@Component
@ConditionalOnProperty(name = "forum.features.mq-consumer", havingValue = "true")
public class ImAiResultConsumer {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatMessageReportService reportService;

    @Autowired
    private MqEventDedupHelper mqEventDedupHelper;

    @RabbitListener(queues = Constant.QUEUE_AI_IM_RESULT, ackMode = "MANUAL")
    public void handle(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(message.getBody(), Map.class);
            String eventId = resolveEventId(result);
            if (!mqEventDedupHelper.tryMarkConsumed("im-ai-result:" + eventId)) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            reportService.applyAsyncResult(result);
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            log.error("IM域AI异步结果处理失败 deliveryTag={}", deliveryTag, exception);
            // 不无限 requeue，避免毒消息打满；依赖 DLX/人工补偿
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private static String resolveEventId(Map<String, Object> result) {
        Object eventId = result.get("eventId");
        if (eventId != null && !String.valueOf(eventId).isBlank()) {
            return String.valueOf(eventId).trim();
        }
        Object taskId = result.get("taskId");
        if (taskId != null && !String.valueOf(taskId).isBlank()) {
            return String.valueOf(taskId).trim();
        }
        Object messageId = result.get("messageId");
        if (messageId != null) {
            return "msg-" + messageId;
        }
        return "unknown-" + System.currentTimeMillis();
    }
}
