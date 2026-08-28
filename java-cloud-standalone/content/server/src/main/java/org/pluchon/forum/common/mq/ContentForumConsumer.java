package org.pluchon.forum.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.cloud.ForumDomainNames;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.entity.vo.mq.ArticleAuditResultMqVO;
import org.pluchon.forum.service.interfaces.article.ArticleService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@ConditionalOnProperty(name = "forum.features.mq-consumer", havingValue = "true")
@ConditionalOnProperty(name = "forum.domain", havingValue = ForumDomainNames.CONTENT)
public class ContentForumConsumer {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private ArticleService articleService;

    @RabbitListener(queues = Constant.QUORUM_QUEUE_AUDIT_RESULT, ackMode = "MANUAL")
    public void handleArticleAuditResult(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            ArticleAuditResultMqVO vo = objectMapper.readValue(message.getBody(), ArticleAuditResultMqVO.class);
            log.debug("[MQ] audit result articleId={} taskId={} status={}",
                    vo.getArticleId(), vo.getTaskId(), vo.getFinalStatus());
            articleService.applyAuditResult(vo);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[MQ] audit result failed deliveryTag={} error={}", deliveryTag, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
