package org.pluchon.forum.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.cloud.ForumDomainNames;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.entity.vo.mq.GameFinishedMqVO;
import org.pluchon.forum.service.interfaces.game.GameMqEventService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 游戏结束结算仅由 forum game 消费，避免 content 进程抢队列后空 ACK
@Slf4j
@Component
@ConditionalOnProperty(name = "forum.domain", havingValue = ForumDomainNames.GAME)
public class GameFinishedMqConsumer {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GameMqEventService gameMqEventService;

    @RabbitListener(queues = Constant.QUORUM_QUEUE_GAME_FINISHED, ackMode = "MANUAL")
    public void handleGameFinished(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            GameFinishedMqVO vo = objectMapper.readValue(message.getBody(), GameFinishedMqVO.class);
            log.debug("[游戏MQ] 收到对局结束事件 | gameCode={} | roomId={} | eventId={}",
                    vo.getGameCode(), vo.getRoomId(), vo.getEventId());
            gameMqEventService.handleGameFinished(vo);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[游戏MQ] 对局结束事件处理失败 | deliveryTag={} | error={}", deliveryTag, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
