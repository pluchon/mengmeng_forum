package org.pluchon.forum.service.impl.message;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.GroupChatMessageStatus;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.GroupChatMessage;
import org.pluchon.forum.entity.db.Message;
import org.pluchon.forum.mapper.GroupChatMessageMapper;
import org.pluchon.forum.mapper.MessageMapper;
import org.pluchon.forum.service.impl.websocket.WebSocketPushService;
import org.pluchon.forum.service.remote.ImAiGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

// 私信/群聊文本先发后审：提交后异步 AI 校验，失败则标记并 WS 通知
@Service
@Slf4j
public class OutboundMessageTextAuditService {

    @Autowired
    private ImAiGatewayService imAiGatewayService;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private GroupChatMessageMapper groupChatMessageMapper;

    @Autowired
    private WebSocketPushService webSocketPushService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("imTextAuditExecutor")
    private ExecutorService imTextAuditExecutor;

    public void schedulePrivateTextAudit(Long messageId, String content, Long sendUserId, Long receiveUserId) {
        if (messageId == null || sendUserId == null || receiveUserId == null || !StringUtils.hasText(content)) {
            return;
        }
        String text = content.trim();
        log.info("私信文本后审已调度 messageId={} sendUserId={} contentLen={}", messageId, sendUserId, text.length());
        TransactionHooks.afterCommit(() -> imTextAuditExecutor.execute(() ->
                auditPrivateText(messageId, text, sendUserId, receiveUserId)));
    }

    public void scheduleGroupTextAudit(Long messageId, Long groupId, String content, Long sendUserId,
                                       List<Long> memberUserIds) {
        if (messageId == null || groupId == null || sendUserId == null || !StringUtils.hasText(content)) {
            return;
        }
        String text = content.trim();
        log.info("群聊文本后审已调度 messageId={} groupId={} contentLen={}", messageId, groupId, text.length());
        List<Long> targets = memberUserIds == null ? List.of() : List.copyOf(memberUserIds);
        TransactionHooks.afterCommit(() -> imTextAuditExecutor.execute(() ->
                auditGroupText(messageId, groupId, text, sendUserId, targets)));
    }

    private void auditPrivateText(Long messageId, String content, Long sendUserId, Long receiveUserId) {
        try {
            log.info("私信文本后审调用 AI messageId={}", messageId);
            String violation = imAiGatewayService.validateText(content);
            if (violation == null) {
                log.info("私信文本后审通过 messageId={}", messageId);
                return;
            }
            int updated = messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                    .eq(Message::getId, messageId)
                    .eq(Message::getPostUserId, sendUserId)
                    .ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE)
                    .ne(Message::getState, Constant.MESSAGE_STATE_AUDIT_FAILED)
                    .ne(Message::getState, Constant.MESSAGE_STATE_RECALLED)
                    .set(Message::getState, Constant.MESSAGE_STATE_AUDIT_FAILED)
                    .set(Message::getContent, ""));
            if (updated <= 0) {
                return;
            }
            // 接收方若仍未读，未读数在会话重载时校正；此处先推送失败事件
            pushPrivateAuditFailed(messageId, sendUserId, receiveUserId, violation);
        } catch (Exception e) {
            log.warn("私信文本后审失败 messageId={}", messageId, e);
        }
    }

    private void auditGroupText(Long messageId, Long groupId, String content, Long sendUserId,
                                List<Long> memberUserIds) {
        try {
            log.info("群聊文本后审调用 AI messageId={} groupId={}", messageId, groupId);
            String violation = imAiGatewayService.validateText(content);
            if (violation == null) {
                log.info("群聊文本后审通过 messageId={} groupId={}", messageId, groupId);
                return;
            }
            int updated = groupChatMessageMapper.update(null, new LambdaUpdateWrapper<GroupChatMessage>()
                    .eq(GroupChatMessage::getId, messageId)
                    .eq(GroupChatMessage::getGroupId, groupId)
                    .eq(GroupChatMessage::getSenderUserId, sendUserId)
                    .ne(GroupChatMessage::getDeleteState, Constant.DELETE_STATE_TRUE)
                    .eq(GroupChatMessage::getStatus, GroupChatMessageStatus.NORMAL.getCode())
                    .set(GroupChatMessage::getStatus, GroupChatMessageStatus.AUDIT_FAILED.getCode())
                    .set(GroupChatMessage::getContent, ""));
            if (updated <= 0) {
                return;
            }
            pushGroupAuditFailed(messageId, groupId, sendUserId, memberUserIds, violation);
        } catch (Exception e) {
            log.warn("群聊文本后审失败 messageId={} groupId={}", messageId, groupId, e);
        }
    }

    private void pushPrivateAuditFailed(Long messageId, Long sendUserId, Long receiveUserId, String reason) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "private_message_audit_failed");
            payload.put("messageId", messageId);
            payload.put("fromUserId", sendUserId);
            payload.put("receiveUserId", receiveUserId);
            payload.put("reason", reason == null ? "" : reason);
            String json = objectMapper.writeValueAsString(payload);
            webSocketPushService.push(sendUserId, json);
            webSocketPushService.push(receiveUserId, json);
        } catch (Exception e) {
            log.warn("私信审核失败 WS 推送异常 messageId={}", messageId, e);
        }
    }

    private void pushGroupAuditFailed(Long messageId, Long groupId, Long sendUserId,
                                      List<Long> memberUserIds, String reason) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "group_message_audit_failed");
            payload.put("messageId", messageId);
            payload.put("groupId", groupId);
            payload.put("fromUserId", sendUserId);
            payload.put("reason", reason == null ? "" : reason);
            String json = objectMapper.writeValueAsString(payload);
            for (Long userId : memberUserIds) {
                if (userId == null) {
                    continue;
                }
                webSocketPushService.push(userId, json);
            }
        } catch (Exception e) {
            log.warn("群聊审核失败 WS 推送异常 messageId={} groupId={}", messageId, groupId, e);
        }
    }
}
