package org.example.forumdemo.service.impl.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.enums.OutboxMessageState;
import org.example.forumdemo.entity.db.ForumOutboxMessage;
import org.example.forumdemo.mapper.ForumOutboxMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * MQ 本地消息表：与业务同事务写入，由 {@link org.example.forumdemo.task.OutboxDispatchTask} 异步投递。
 */
@Slf4j
@Service
public class LocalMessageOutboxService {

    @Autowired
    private ForumOutboxMessageMapper forumOutboxMessageMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void enqueue(String routingKey, String eventId, Object payload) {
        if (!StringUtils.hasText(routingKey) || !StringUtils.hasText(eventId) || payload == null) {
            return;
        }
        try {
            ForumOutboxMessage row = new ForumOutboxMessage();
            row.setEventId(eventId.trim());
            row.setRoutingKey(routingKey.trim());
            row.setPayloadJson(objectMapper.writeValueAsString(payload));
            row.setMessageState(OutboxMessageState.PENDING.getCode());
            row.setRetryCount(0);
            row.setDeleteState((byte) 0);
            forumOutboxMessageMapper.insert(row);
        } catch (Exception e) {
            log.error("写入 Outbox 失败 eventId={}", eventId, e);
            throw new IllegalStateException("Outbox 写入失败", e);
        }
    }
}
