package org.example.forumdemo.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.OutboxMessageState;
import org.example.forumdemo.common.mq.ForumProducer;
import org.example.forumdemo.entity.db.ForumOutboxMessage;
import org.example.forumdemo.entity.vo.mq.MessageNotifyMqVO;
import org.example.forumdemo.entity.vo.mq.ReplyNotifyMqVO;
import org.example.forumdemo.mapper.ForumOutboxMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** 本地消息表投递：防多实例重复执行 */
@Slf4j
@Component
public class OutboxDispatchTask {

    private static final String LOCK_KEY = "forum:task:outbox_dispatch:lock";
    private static final int MAX_RETRY = 5;
    private static final int BATCH_SIZE = 50;

    @Autowired
    private ForumOutboxMessageMapper forumOutboxMessageMapper;

    @Autowired
    private ForumProducer forumProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    public void dispatchPendingMessages() {
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", Duration.ofMinutes(2));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            Page<ForumOutboxMessage> page = new Page<>(1, BATCH_SIZE, false);
            List<ForumOutboxMessage> pending = forumOutboxMessageMapper.selectPage(page,
                    Wrappers.lambdaQuery(ForumOutboxMessage.class)
                            .eq(ForumOutboxMessage::getMessageState, OutboxMessageState.PENDING.getCode())
                            .orderByAsc(ForumOutboxMessage::getId)).getRecords();
            for (ForumOutboxMessage row : pending) {
                dispatchOne(row);
            }
        } catch (Exception e) {
            log.error("Outbox 批量投递失败", e);
        } finally {
            stringRedisTemplate.delete(LOCK_KEY);
        }
    }

    private void dispatchOne(ForumOutboxMessage row) {
        try {
            Object payload = parsePayload(row);
            if (payload == null) {
                markDead(row, "payload 解析失败");
                return;
            }
            sendByRoutingKey(row.getRoutingKey(), payload);
            forumOutboxMessageMapper.update(null, Wrappers.lambdaUpdate(ForumOutboxMessage.class)
                    .eq(ForumOutboxMessage::getId, row.getId())
                    .eq(ForumOutboxMessage::getMessageState, OutboxMessageState.PENDING.getCode())
                    .set(ForumOutboxMessage::getMessageState, OutboxMessageState.SENT.getCode()));
        } catch (Exception e) {
            int retry = row.getRetryCount() != null ? row.getRetryCount() : 0;
            retry++;
            OutboxMessageState nextState = retry >= MAX_RETRY
                    ? OutboxMessageState.DEAD
                    : OutboxMessageState.PENDING;
            String err = e.getMessage() != null ? e.getMessage().substring(0, Math.min(200, e.getMessage().length()))
                    : "dispatch error";
            forumOutboxMessageMapper.update(null, Wrappers.lambdaUpdate(ForumOutboxMessage.class)
                    .eq(ForumOutboxMessage::getId, row.getId())
                    .set(ForumOutboxMessage::getRetryCount, retry)
                    .set(ForumOutboxMessage::getLastError, err)
                    .set(ForumOutboxMessage::getMessageState, nextState.getCode()));
            log.warn("Outbox 投递失败 id={} retry={}", row.getId(), retry);
        }
    }

    private Object parsePayload(ForumOutboxMessage row) throws Exception {
        if (Constant.ROUTING_KEY_QUEUE_2.equals(row.getRoutingKey())) {
            return objectMapper.readValue(row.getPayloadJson(), MessageNotifyMqVO.class);
        }
        if (Constant.ROUTING_KEY_QUEUE_1.equals(row.getRoutingKey())) {
            return objectMapper.readValue(row.getPayloadJson(), ReplyNotifyMqVO.class);
        }
        return objectMapper.readValue(row.getPayloadJson(), Object.class);
    }

    private void sendByRoutingKey(String routingKey, Object payload) {
        if (Constant.ROUTING_KEY_QUEUE_2.equals(routingKey)) {
            forumProducer.sendMessageNotify(payload);
            return;
        }
        if (Constant.ROUTING_KEY_QUEUE_1.equals(routingKey)) {
            forumProducer.sendReplyNotify(payload);
            return;
        }
        throw new IllegalArgumentException("未知 routingKey: " + routingKey);
    }

    private void markDead(ForumOutboxMessage row, String reason) {
        forumOutboxMessageMapper.update(null, Wrappers.lambdaUpdate(ForumOutboxMessage.class)
                .eq(ForumOutboxMessage::getId, row.getId())
                .set(ForumOutboxMessage::getMessageState, OutboxMessageState.DEAD.getCode())
                .set(ForumOutboxMessage::getLastError, reason));
    }
}
