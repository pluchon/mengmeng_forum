package org.pluchon.forum.service.impl.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pluchon.forum.common.enums.GroupChatMemberStatus;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.mq.ForumProducer;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.ChatMessageReport;
import org.pluchon.forum.entity.db.GroupChatMember;
import org.pluchon.forum.entity.db.GroupChatMessage;
import org.pluchon.forum.entity.db.ImAiTask;
import org.pluchon.forum.entity.db.Message;
import org.pluchon.forum.entity.dto.message.ChatMessageReportRequest;
import org.pluchon.forum.entity.vo.message.ChatMessageReportVO;
import org.pluchon.forum.mapper.ChatMessageReportMapper;
import org.pluchon.forum.mapper.GroupChatMemberMapper;
import org.pluchon.forum.mapper.GroupChatMessageMapper;
import org.pluchon.forum.mapper.ImAiTaskMapper;
import org.pluchon.forum.mapper.MessageMapper;
import org.pluchon.forum.service.impl.websocket.WebSocketPushService;
import org.pluchon.forum.service.interfaces.message.ChatMessageReportService;
import org.pluchon.forum.service.interfaces.message.SystemMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

// 聊天文本消息举报实现
@Service
public class ChatMessageReportServiceImpl implements ChatMessageReportService {

    private static final byte PRIVATE = 1;
    private static final byte GROUP = 2;

    // 给 AI 的举报理由最多带几条，避免刷举报把提示词撑爆
    private static final int MAX_REPORT_REASONS = 5;
    private static final byte TEXT_MESSAGE = 0;
    private static final byte TASK_PENDING = 0;
    private static final byte TASK_COMPLETED = 2;
    private static final byte TASK_FAILED = 3;
    private static final byte REPORT_PROCESSING = 0;
    private static final byte REPORT_SUCCESS = 1;
    private static final byte REPORT_REJECTED = 2;
    private static final byte REPORT_ERROR = 3;
    private static final byte DELETE_FALSE = 0;
    private static final byte DELETE_TRUE = 1;
    private static final String GROUP_INVITE_PREFIX = "[[GROUP_INVITE:";

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private GroupChatMessageMapper groupMessageMapper;

    @Autowired
    private GroupChatMemberMapper groupMemberMapper;

    @Autowired
    private ImAiTaskMapper taskMapper;

    @Autowired
    private ChatMessageReportMapper reportMapper;

    @Autowired
    private ForumProducer forumProducer;

    @Autowired
    private SystemMessageService systemMessageService;

    @Autowired
    private WebSocketPushService webSocketPushService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageReportVO report(Long reporterUserId, ChatMessageReportRequest request) {
        byte conversationType = parseConversationType(request.getConversationType());
        ChatSnapshot snapshot = loadAndAuthorize(
                conversationType, request.getMessageId(), reporterUserId);
        String hash = sha256(snapshot.content());
        ChatMessageReport existing = reportMapper.selectOne(new LambdaQueryWrapper<ChatMessageReport>()
                .eq(ChatMessageReport::getReporterUserId, reporterUserId)
                .eq(ChatMessageReport::getConversationType, conversationType)
                .eq(ChatMessageReport::getMessageId, request.getMessageId())
                .eq(ChatMessageReport::getContentHash, hash)
                .eq(ChatMessageReport::getDeleteState, DELETE_FALSE));
        if (existing != null) {
            return toVO(existing);
        }
        ImAiTask shared = findSharedTask(conversationType, request.getMessageId(), hash);
        ChatMessageReport report = new ChatMessageReport();
        report.setReporterUserId(reporterUserId);
        report.setConversationType(conversationType);
        report.setMessageId(request.getMessageId());
        report.setContentHash(hash);
        report.setReason(request.getReason().trim());
        report.setDeleteState(DELETE_FALSE);
        if (snapshot.deleted()) {
            report.setTaskId("resolved-" + UUID.randomUUID());
            report.setResultStatus(REPORT_SUCCESS);
            report.setResultMessage("消息已处理");
            reportMapper.insert(report);
            notifyReporter(report, snapshot.summary(), "消息此前已被处理");
            return toVO(report);
        }
        if (shared == null) {
            shared = createTask(reporterUserId, conversationType, request.getMessageId(), hash);
            // 此处举报单尚未 insert，理由取当前这条，不能走聚合查询
            Map<String, Object> payload = taskPayload(shared, snapshot, report.getReason());
            TransactionHooks.afterCommit(() -> forumProducer.sendAiAsyncTask(payload));
        }
        report.setTaskId(shared.getTaskId());
        report.setResultStatus(statusForTask(shared));
        report.setResultMessage(shared.getResultReason());
        reportMapper.insert(report);
        if (REPORT_PROCESSING != safeByte(report.getResultStatus())) {
            notifyReporter(report, snapshot.summary(), report.getResultMessage());
        }
        return toVO(report);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyAsyncResult(Map<String, Object> result) {
        if (!"CHAT_REPORT_MODERATION".equals(text(result.get("taskType")))) {
            return;
        }
        ImAiTask task = taskMapper.selectOne(new LambdaQueryWrapper<ImAiTask>()
                .eq(ImAiTask::getTaskId, text(result.get("taskId")))
                .eq(ImAiTask::getDeleteState, DELETE_FALSE));
        if (task == null || TASK_COMPLETED == safeByte(task.getStatus())) {
            return;
        }
        String finalStatus = text(result.get("finalStatus"));
        if ("ERROR".equals(finalStatus)) {
            retryOrFail(task, text(result.get("finalReason")));
            return;
        }
        ChatSnapshot snapshot = loadWithoutReporter(task.getTargetType(), task.getTargetId());
        byte reportStatus = "VIOLATION".equals(finalStatus) ? REPORT_SUCCESS : REPORT_REJECTED;
        if (REPORT_SUCCESS == reportStatus && !snapshot.deleted()) {
            deleteMessage(task.getTargetType(), task.getTargetId(), snapshot);
        }
        finish(task, reportStatus, text(result.get("finalReason")), snapshot.summary());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int republishPendingTasks() {
        java.util.Date before = new java.util.Date(System.currentTimeMillis() - 60_000L);
        List<ImAiTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<ImAiTask>()
                .eq(ImAiTask::getTaskType, (byte) 1)
                .eq(ImAiTask::getStatus, TASK_PENDING)
                .eq(ImAiTask::getDeleteState, DELETE_FALSE)
                .le(ImAiTask::getUpdateTime, before)
                .last("LIMIT 20"));
        int count = 0;
        for (ImAiTask task : tasks) {
            ChatSnapshot snapshot = loadWithoutReporter(task.getTargetType(), task.getTargetId());
            if (!task.getContentHash().equals(sha256(snapshot.content()))) {
                continue;
            }
            forumProducer.sendAiAsyncTask(taskPayload(task, snapshot));
            taskMapper.update(null, new LambdaUpdateWrapper<ImAiTask>()
                    .eq(ImAiTask::getId, task.getId())
                    .set(ImAiTask::getUpdateTime, new java.util.Date()));
            count++;
        }
        return count;
    }

    private void retryOrFail(ImAiTask task, String reason) {
        int retries = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retries >= 2) {
            taskMapper.update(null, new LambdaUpdateWrapper<ImAiTask>()
                    .eq(ImAiTask::getId, task.getId())
                    .set(ImAiTask::getStatus, TASK_FAILED)
                    .set(ImAiTask::getRetryCount, retries + 1)
                    .set(ImAiTask::getResultCode, "ERROR")
                    .set(ImAiTask::getResultReason, reason));
            finishReports(task, REPORT_ERROR, "审核服务异常，请稍后重试",
                    loadWithoutReporter(task.getTargetType(), task.getTargetId()).summary());
            return;
        }
        task.setRetryCount(retries + 1);
        taskMapper.update(null, new LambdaUpdateWrapper<ImAiTask>()
                .eq(ImAiTask::getId, task.getId())
                .set(ImAiTask::getStatus, TASK_PENDING)
                .set(ImAiTask::getRetryCount, task.getRetryCount())
                .set(ImAiTask::getResultReason, reason));
        ChatSnapshot snapshot = loadWithoutReporter(task.getTargetType(), task.getTargetId());
        TransactionHooks.afterCommit(() -> forumProducer.sendAiAsyncTask(taskPayload(task, snapshot)));
    }

    private void finish(ImAiTask task, byte status, String reason, String summary) {
        taskMapper.update(null, new LambdaUpdateWrapper<ImAiTask>()
                .eq(ImAiTask::getId, task.getId())
                .set(ImAiTask::getStatus, TASK_COMPLETED)
                .set(ImAiTask::getResultCode, status == REPORT_SUCCESS ? "VIOLATION" : "COMPLIANT")
                .set(ImAiTask::getResultReason, reason));
        finishReports(task, status, reason, summary);
    }

    private void finishReports(ImAiTask task, byte status, String reason, String summary) {
        List<ChatMessageReport> reports = reportMapper.selectList(
                new LambdaQueryWrapper<ChatMessageReport>()
                        .eq(ChatMessageReport::getTaskId, task.getTaskId())
                        .eq(ChatMessageReport::getResultStatus, REPORT_PROCESSING)
                        .eq(ChatMessageReport::getDeleteState, DELETE_FALSE));
        reportMapper.update(null, new LambdaUpdateWrapper<ChatMessageReport>()
                .eq(ChatMessageReport::getTaskId, task.getTaskId())
                .eq(ChatMessageReport::getResultStatus, REPORT_PROCESSING)
                .set(ChatMessageReport::getResultStatus, status)
                .set(ChatMessageReport::getResultMessage, reason));
        reports.forEach(report -> {
            report.setResultStatus(status);
            notifyReporter(report, summary, reason);
        });
    }

    private void deleteMessage(byte type, Long messageId, ChatSnapshot snapshot) {
        if (PRIVATE == type) {
            messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                    .eq(Message::getId, messageId)
                    .ne(Message::getDeleteState, DELETE_TRUE)
                    .set(Message::getDeleteState, DELETE_TRUE));
            TransactionHooks.afterCommit(() -> {
                pushDeleted(snapshot.senderUserId(), "private_message_deleted", null, messageId);
                pushDeleted(snapshot.receiverUserId(), "private_message_deleted", null, messageId);
            });
            return;
        }
        groupMessageMapper.update(null, new LambdaUpdateWrapper<GroupChatMessage>()
                .eq(GroupChatMessage::getId, messageId)
                .ne(GroupChatMessage::getDeleteState, DELETE_TRUE)
                .set(GroupChatMessage::getDeleteState, DELETE_TRUE));
        List<GroupChatMember> members = activeGroupMembers(snapshot.groupId());
        TransactionHooks.afterCommit(() -> members.forEach(member ->
                pushDeleted(member.getUserId(), "group_message_deleted", snapshot.groupId(), messageId)));
    }

    private void pushDeleted(Long userId, String type, Long groupId, Long messageId) {
        if (userId == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", type);
            payload.put("messageId", messageId);
            if (groupId != null) {
                payload.put("groupId", groupId);
            }
            webSocketPushService.push(userId, objectMapper.writeValueAsString(payload));
        } catch (Exception ignored) {
            // WebSocket失败不回滚已完成的逻辑删除，客户端刷新后仍会消失
        }
    }

    private ChatSnapshot loadAndAuthorize(byte type, Long messageId, Long reporterUserId) {
        ChatSnapshot snapshot = loadWithoutReporter(type, messageId);
        if (Objects.equals(snapshot.senderUserId(), reporterUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "不能举报自己的消息"));
        }
        if (PRIVATE == type && !Objects.equals(snapshot.receiverUserId(), reporterUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        if (GROUP == type && activeGroupMembers(snapshot.groupId()).stream()
                .noneMatch(member -> Objects.equals(member.getUserId(), reporterUserId))) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        return snapshot;
    }

    private ChatSnapshot loadWithoutReporter(byte type, Long messageId) {
        if (PRIVATE == type) {
            Message message = messageMapper.selectById(messageId);
            if (message == null || TEXT_MESSAGE != safeByte(message.getMessageType())
                    || safeByte(message.getState()) == 2
                    || isGroupInviteContent(message.getContent())) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                        "该消息不支持举报"));
            }
            return new ChatSnapshot(message.getContent(), summarize(message.getContent()),
                    message.getPostUserId(), message.getReceiveUserId(), null,
                    DELETE_TRUE == safeByte(message.getDeleteState()));
        }
        GroupChatMessage message = groupMessageMapper.selectById(messageId);
        if (message == null || TEXT_MESSAGE != safeByte(message.getMessageType())
                || safeByte(message.getStatus()) == 3) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "该消息不支持举报"));
        }
        return new ChatSnapshot(message.getContent(), summarize(message.getContent()),
                message.getSenderUserId(), null, message.getGroupId(),
                DELETE_TRUE == safeByte(message.getDeleteState()) || safeByte(message.getStatus()) == 2);
    }

    private List<GroupChatMember> activeGroupMembers(Long groupId) {
        return groupMemberMapper.selectList(new LambdaQueryWrapper<GroupChatMember>()
                .eq(GroupChatMember::getGroupId, groupId)
                .eq(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                .ne(GroupChatMember::getDeleteState, DELETE_TRUE));
    }

    private ImAiTask findSharedTask(byte type, Long messageId, String hash) {
        return taskMapper.selectOne(new LambdaQueryWrapper<ImAiTask>()
                .eq(ImAiTask::getTargetType, type)
                .eq(ImAiTask::getTargetId, messageId)
                .eq(ImAiTask::getContentHash, hash)
                .eq(ImAiTask::getDeleteState, DELETE_FALSE)
                .orderByDesc(ImAiTask::getId)
                .last("LIMIT 1"));
    }

    private ImAiTask createTask(Long userId, byte type, Long messageId, String hash) {
        ImAiTask task = new ImAiTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskType((byte) 1);
        task.setTargetType(type);
        task.setTargetId(messageId);
        task.setContentHash(hash);
        task.setTriggerUserId(userId);
        task.setStatus(TASK_PENDING);
        task.setRetryCount(0);
        task.setDeleteState(DELETE_FALSE);
        taskMapper.insert(task);
        return task;
    }

    // 重投/重试路径没有当前举报对象，理由改从已落库的举报单聚合
    private Map<String, Object> taskPayload(ImAiTask task, ChatSnapshot snapshot) {
        return taskPayload(task, snapshot, aggregatedReasons(task.getTaskId()));
    }

    // 同一条消息的多人举报共享一个 AI 任务，理由去重后一并给出，仅作审核视角提示
    private String aggregatedReasons(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return "";
        }
        List<ChatMessageReport> reports = reportMapper.selectList(new LambdaQueryWrapper<ChatMessageReport>()
                .eq(ChatMessageReport::getTaskId, taskId)
                .eq(ChatMessageReport::getDeleteState, DELETE_FALSE));
        return reports.stream()
                .map(ChatMessageReport::getReason)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(reason -> !reason.isEmpty())
                .distinct()
                .limit(MAX_REPORT_REASONS)
                .collect(Collectors.joining("；"));
    }

    private Map<String, Object> taskPayload(ImAiTask task, ChatSnapshot snapshot, String reportReason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", task.getTaskId());
        payload.put("taskType", "CHAT_REPORT_MODERATION");
        payload.put("reportReason", reportReason == null ? "" : reportReason);
        payload.put("targetType", PRIVATE == safeByte(task.getTargetType()) ? "PRIVATE" : "GROUP");
        payload.put("targetId", task.getTargetId());
        payload.put("contentHash", task.getContentHash());
        payload.put("content", snapshot.content());
        payload.put("resultDomain", "IM");
        return payload;
    }

    private void notifyReporter(ChatMessageReport report, String summary, String resultReason) {
        byte type = switch (safeByte(report.getResultStatus())) {
            case REPORT_SUCCESS -> 5;
            case REPORT_REJECTED -> 6;
            default -> 7;
        };
        String title = type == 5 ? "举报成功" : type == 6 ? "举报不通过" : "举报异常";
        String targetLabel = PRIVATE == safeByte(report.getConversationType()) ? "私信消息" : "群聊消息";
        String targetSummary = summary == null || summary.isBlank() ? "相关内容" : summary.trim();
        String outcome = type == 5 ? "举报成功" : type == 6 ? "不通过" : "处理异常";
        String reason = normalizeResultReason(resultReason, type);
        String content = "您举报的" + targetLabel + "“" + targetSummary + "”" + outcome
                + "，因为" + reason + "。";
        TransactionHooks.afterCommit(() -> systemMessageService.createMessage(
                report.getReporterUserId(), type, title, content, report.getMessageId(), null));
    }

    private static String normalizeResultReason(String resultReason, byte messageType) {
        if (resultReason != null && !resultReason.isBlank()) {
            return resultReason.trim().replaceAll("[。！？.!?]+$", "");
        }
        if (messageType == 5) {
            return "审核确认该消息违反社区规范";
        }
        if (messageType == 6) {
            return "审核未发现明确违规内容";
        }
        return "审核服务暂时异常，请稍后重试";
    }

    private static boolean isGroupInviteContent(String content) {
        String text = content == null ? "" : content.trim();
        return text.startsWith(GROUP_INVITE_PREFIX) && text.endsWith("]]");
    }

    private ChatMessageReportVO toVO(ChatMessageReport report) {
        ChatMessageReportVO vo = new ChatMessageReportVO();
        vo.setReportId(report.getId());
        vo.setStatus(switch (safeByte(report.getResultStatus())) {
            case REPORT_SUCCESS -> "SUCCESS";
            case REPORT_REJECTED -> "REJECTED";
            case REPORT_ERROR -> "ERROR";
            default -> "PROCESSING";
        });
        vo.setMessage(report.getResultMessage());
        return vo;
    }

    private byte statusForTask(ImAiTask task) {
        if (TASK_COMPLETED == safeByte(task.getStatus())) {
            return "VIOLATION".equals(task.getResultCode()) ? REPORT_SUCCESS : REPORT_REJECTED;
        }
        return TASK_FAILED == safeByte(task.getStatus()) ? REPORT_ERROR : REPORT_PROCESSING;
    }

    private byte parseConversationType(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "PRIVATE" -> PRIVATE;
            case "GROUP" -> GROUP;
            default -> throw new ApplicationException(Result.fail(
                    ResultCode.FAILED_PARAMS_VALIDATE, "conversationType 仅允许 PRIVATE、GROUP"));
        };
    }

    private static String summarize(String content) {
        String text = content == null ? "" : content.trim();
        return text.length() > 80 ? text.substring(0, 80) + "..." : text;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算消息摘要", exception);
        }
    }

    private static byte safeByte(Byte value) {
        return value == null ? 0 : value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record ChatSnapshot(String content, String summary, Long senderUserId,
                                Long receiverUserId, Long groupId, boolean deleted) {
    }
}
