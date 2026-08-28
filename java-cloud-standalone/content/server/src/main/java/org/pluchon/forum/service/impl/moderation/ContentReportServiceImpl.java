package org.pluchon.forum.service.impl.moderation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.pluchon.forum.cloud.feign.ContentSystemMessageInternalFeignClient;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.mq.ForumProducer;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ArticleReply;
import org.pluchon.forum.entity.db.ArticleSubReply;
import org.pluchon.forum.entity.db.ArticleVideoDanmaku;
import org.pluchon.forum.entity.db.ContentAiTask;
import org.pluchon.forum.entity.db.ContentReport;
import org.pluchon.forum.entity.dto.article.ContentReportRequest;
import org.pluchon.forum.entity.vo.article.ContentReportVO;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.mapper.ArticleReplyMapper;
import org.pluchon.forum.mapper.ArticleSubReplyMapper;
import org.pluchon.forum.mapper.ArticleVideoDanmakuMapper;
import org.pluchon.forum.mapper.ContentAiTaskMapper;
import org.pluchon.forum.mapper.ContentReportMapper;
import org.pluchon.forum.service.interfaces.article.ArticleService;
import org.pluchon.forum.service.interfaces.moderation.ContentModerationTaskService;
import org.pluchon.forum.service.interfaces.moderation.ContentReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 内容举报与共享AI审核任务实现
@Service
public class ContentReportServiceImpl implements ContentReportService {

    // 给 AI 的举报理由最多带几条，避免刷举报把提示词撑爆
    private static final int MAX_REPORT_REASONS = 5;
    private static final byte TASK_TYPE_REPORT = 3;
    private static final byte TARGET_ARTICLE = 1;
    private static final byte TARGET_REPLY = 2;
    private static final byte TARGET_SUB_REPLY = 3;
    private static final byte TARGET_DANMAKU = 4;
    private static final byte TASK_PENDING = 0;
    private static final byte TASK_COMPLETED = 2;
    private static final byte TASK_FAILED = 3;
    private static final byte REPORT_PROCESSING = 0;
    private static final byte REPORT_SUCCESS = 1;
    private static final byte REPORT_REJECTED = 2;
    private static final byte REPORT_ERROR = 3;
    private static final byte DELETE_FALSE = 0;
    private static final byte DELETE_TRUE = 1;

    @Autowired
    private ContentReportMapper reportMapper;

    @Autowired
    private ContentAiTaskMapper taskMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleReplyMapper articleReplyMapper;

    @Autowired
    private ArticleSubReplyMapper articleSubReplyMapper;

    @Autowired
    private ArticleVideoDanmakuMapper articleVideoDanmakuMapper;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ContentModerationTaskService moderationTaskService;

    @Autowired
    private ForumProducer forumProducer;

    @Autowired
    private ContentSystemMessageInternalFeignClient systemMessageClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentReportVO report(Long reporterUserId, ContentReportRequest request) {
        if (reporterUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        byte targetType = parseTargetType(request.getTargetType());
        TargetSnapshot snapshot = loadSnapshot(targetType, request.getTargetId());
        if (snapshot.ownerUserId() != null && snapshot.ownerUserId().equals(reporterUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "不能举报自己的内容"));
        }
        String contentHash = sha256(snapshot.content());
        ContentReport existing = findReporterRecord(
                reporterUserId, targetType, request.getTargetId(), contentHash);
        if (existing != null) {
            return toVO(existing);
        }

        ContentAiTask shared = findSharedTask(targetType, request.getTargetId(), contentHash);
        ContentReport report = new ContentReport();
        report.setReporterUserId(reporterUserId);
        report.setTargetType(targetType);
        report.setTargetId(request.getTargetId());
        report.setContentHash(contentHash);
        report.setReason(request.getReason().trim());
        report.setDeleteState(DELETE_FALSE);

        if (snapshot.deleted()) {
            report.setTaskId("resolved-" + UUID.randomUUID());
            report.setResultStatus(REPORT_SUCCESS);
            report.setResultMessage("目标内容已处理");
            insertReport(report);
            notifyReporter(report, snapshot.title(), "目标内容此前已被处理");
            return toVO(report);
        }
        if (shared == null) {
            shared = createTask(reporterUserId, targetType, request.getTargetId(), contentHash);
            // 此处举报单尚未 insert，理由取当前这条，不能走聚合查询
            Map<String, Object> payload = taskPayload(shared, snapshot, report.getReason());
            TransactionHooks.afterCommit(() -> forumProducer.sendAiAsyncTask(payload));
        }
        report.setTaskId(shared.getTaskId());
        report.setResultStatus(resultStatusForTask(shared));
        report.setResultMessage(shared.getResultReason());
        insertReport(report);
        if (REPORT_PROCESSING != safeByte(report.getResultStatus())) {
            notifyReporter(report, snapshot.title(), report.getResultMessage());
        }
        return toVO(report);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyAsyncResult(Map<String, Object> result) {
        if (!"CONTENT_REPORT_MODERATION".equals(text(result.get("taskType")))) {
            return;
        }
        ContentAiTask task = taskMapper.selectOne(new LambdaQueryWrapper<ContentAiTask>()
                .eq(ContentAiTask::getTaskId, text(result.get("taskId")))
                .eq(ContentAiTask::getDeleteState, DELETE_FALSE));
        if (task == null || TASK_COMPLETED == safeByte(task.getStatus())) {
            return;
        }
        String finalStatus = text(result.get("finalStatus"));
        if ("ERROR".equals(finalStatus)) {
            retryOrFail(task, text(result.get("finalReason")));
            return;
        }
        byte reportStatus = "VIOLATION".equals(finalStatus) ? REPORT_SUCCESS : REPORT_REJECTED;
        TargetSnapshot snapshot = loadSnapshot(task.getTargetType(), task.getTargetId());
        if (REPORT_SUCCESS == reportStatus && !snapshot.deleted()) {
            if (TARGET_ARTICLE == safeByte(task.getTargetType())) {
                articleService.deleteArticle(task.getTargetId(), snapshot.ownerUserId());
            } else {
                moderationTaskService.deleteConfirmedViolation(
                        task.getTargetType(), task.getTargetId(), task.getContentHash());
            }
        }
        finishTaskAndReports(task, reportStatus, text(result.get("finalReason")), snapshot.title());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int republishPendingTasks() {
        java.util.Date before = new java.util.Date(System.currentTimeMillis() - 60_000L);
        List<ContentAiTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<ContentAiTask>()
                .eq(ContentAiTask::getTaskType, TASK_TYPE_REPORT)
                .eq(ContentAiTask::getStatus, TASK_PENDING)
                .eq(ContentAiTask::getDeleteState, DELETE_FALSE)
                .le(ContentAiTask::getUpdateTime, before)
                .last("LIMIT 20"));
        int count = 0;
        for (ContentAiTask task : tasks) {
            TargetSnapshot snapshot = loadSnapshot(task.getTargetType(), task.getTargetId());
            if (!task.getContentHash().equals(sha256(snapshot.content()))) {
                continue;
            }
            forumProducer.sendAiAsyncTask(taskPayload(task, snapshot));
            taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                    .eq(ContentAiTask::getId, task.getId())
                    .set(ContentAiTask::getUpdateTime, new java.util.Date()));
            count++;
        }
        return count;
    }

    private void retryOrFail(ContentAiTask task, String reason) {
        int retries = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retries >= 2) {
            taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                    .eq(ContentAiTask::getId, task.getId())
                    .set(ContentAiTask::getStatus, TASK_FAILED)
                    .set(ContentAiTask::getRetryCount, retries + 1)
                    .set(ContentAiTask::getResultCode, "ERROR")
                    .set(ContentAiTask::getResultReason, reason));
            List<ContentReport> reports = reportMapper.selectList(new LambdaQueryWrapper<ContentReport>()
                    .eq(ContentReport::getTaskId, task.getTaskId())
                    .eq(ContentReport::getResultStatus, REPORT_PROCESSING)
                    .eq(ContentReport::getDeleteState, DELETE_FALSE));
            reportMapper.update(null, new LambdaUpdateWrapper<ContentReport>()
                    .eq(ContentReport::getTaskId, task.getTaskId())
                    .eq(ContentReport::getResultStatus, REPORT_PROCESSING)
                    .set(ContentReport::getResultStatus, REPORT_ERROR)
                    .set(ContentReport::getResultMessage, "审核服务异常，请稍后重试"));
            TargetSnapshot snapshot = loadSnapshot(task.getTargetType(), task.getTargetId());
            reports.forEach(report -> {
                report.setResultStatus(REPORT_ERROR);
                notifyReporter(report, snapshot.title(), "审核服务暂时异常，请稍后重试");
            });
            return;
        }
        TargetSnapshot snapshot = loadSnapshot(task.getTargetType(), task.getTargetId());
        task.setRetryCount(retries + 1);
        taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                .eq(ContentAiTask::getId, task.getId())
                .set(ContentAiTask::getStatus, TASK_PENDING)
                .set(ContentAiTask::getRetryCount, task.getRetryCount())
                .set(ContentAiTask::getResultReason, reason));
        TransactionHooks.afterCommit(() -> forumProducer.sendAiAsyncTask(taskPayload(task, snapshot)));
    }

    private void finishTaskAndReports(ContentAiTask task, byte status, String reason, String title) {
        taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                .eq(ContentAiTask::getId, task.getId())
                .set(ContentAiTask::getStatus, TASK_COMPLETED)
                .set(ContentAiTask::getResultCode, status == REPORT_SUCCESS ? "VIOLATION" : "COMPLIANT")
                .set(ContentAiTask::getResultReason, reason));
        List<ContentReport> reports = reportMapper.selectList(new LambdaQueryWrapper<ContentReport>()
                .eq(ContentReport::getTaskId, task.getTaskId())
                .eq(ContentReport::getResultStatus, REPORT_PROCESSING)
                .eq(ContentReport::getDeleteState, DELETE_FALSE));
        reportMapper.update(null, new LambdaUpdateWrapper<ContentReport>()
                .eq(ContentReport::getTaskId, task.getTaskId())
                .eq(ContentReport::getResultStatus, REPORT_PROCESSING)
                .set(ContentReport::getResultStatus, status)
                .set(ContentReport::getResultMessage, reason));
        reports.forEach(report -> {
            report.setResultStatus(status);
            notifyReporter(report, title, reason);
        });
    }

    private void notifyReporter(ContentReport report, String targetTitle, String resultReason) {
        byte type = switch (safeByte(report.getResultStatus())) {
            case REPORT_SUCCESS -> 5;
            case REPORT_REJECTED -> 6;
            default -> 7;
        };
        String title = switch (type) {
            case 5 -> "举报成功";
            case 6 -> "举报不通过";
            default -> "举报异常";
        };
        String targetLabel = switch (safeByte(report.getTargetType())) {
            case TARGET_ARTICLE -> "帖子";
            case TARGET_REPLY -> "评论";
            case TARGET_SUB_REPLY -> "回复";
            case TARGET_DANMAKU -> "弹幕";
            default -> "内容";
        };
        String displayTitle = StringUtils.hasText(targetTitle) ? targetTitle.trim() : "相关内容";
        String target = TARGET_ARTICLE == safeByte(report.getTargetType())
                ? targetLabel + "《" + displayTitle + "》"
                : targetLabel + "“" + displayTitle + "”";
        String outcome = type == 5 ? "举报成功" : type == 6 ? "不通过" : "处理异常";
        String reason = normalizeResultReason(resultReason, type);
        String content = "您举报的" + target + outcome + "，因为" + reason + "。";
        TransactionHooks.afterCommit(() -> systemMessageClient.createMessage(
                report.getReporterUserId(), type, title, content,
                report.getTargetId(), null));
    }

    private static String normalizeResultReason(String resultReason, byte messageType) {
        if (StringUtils.hasText(resultReason)) {
            return resultReason.trim().replaceAll("[。！？.!?]+$", "");
        }
        if (messageType == 5) {
            return "审核确认该内容违反社区规范";
        }
        if (messageType == 6) {
            return "审核未发现明确违规内容";
        }
        return "审核服务暂时异常，请稍后重试";
    }

    private TargetSnapshot loadSnapshot(byte targetType, Long targetId) {
        if (TARGET_ARTICLE == targetType) {
            Article article = articleMapper.selectById(targetId);
            if (article == null) {
                return new TargetSnapshot(null, "", "相关帖子", true);
            }
            return new TargetSnapshot(article.getUserId(), article.getTitle() + "\n" + article.getContent(),
                    article.getTitle(), DELETE_TRUE == safeByte(article.getDeleteState()));
        }
        if (TARGET_REPLY == targetType) {
            ArticleReply reply = articleReplyMapper.selectById(targetId);
            if (reply == null) {
                return new TargetSnapshot(null, "", "相关评论", true);
            }
            return new TargetSnapshot(reply.getPostUserId(), reply.getContent(), summarize(reply.getContent()),
                    DELETE_TRUE == safeByte(reply.getDeleteState()));
        }
        if (TARGET_DANMAKU == targetType) {
            ArticleVideoDanmaku danmaku = articleVideoDanmakuMapper.selectById(targetId);
            if (danmaku == null) {
                return new TargetSnapshot(null, "", "相关弹幕", true);
            }
            return new TargetSnapshot(danmaku.getUserId(), danmaku.getContent(), summarize(danmaku.getContent()),
                    DELETE_TRUE == safeByte(danmaku.getDeleteState()));
        }
        ArticleSubReply reply = articleSubReplyMapper.selectById(targetId);
        if (reply == null) {
            return new TargetSnapshot(null, "", "相关回复", true);
        }
        return new TargetSnapshot(reply.getPostUserId(), reply.getContent(), summarize(reply.getContent()),
                DELETE_TRUE == safeByte(reply.getDeleteState()));
    }

    private ContentAiTask createTask(Long userId, byte targetType, Long targetId, String hash) {
        ContentAiTask task = new ContentAiTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskType(TASK_TYPE_REPORT);
        task.setTargetType(targetType);
        task.setTargetId(targetId);
        task.setContentHash(hash);
        task.setTriggerUserId(userId);
        task.setStatus(TASK_PENDING);
        task.setRetryCount(0);
        task.setDeleteState(DELETE_FALSE);
        taskMapper.insert(task);
        return task;
    }

    private ContentAiTask findSharedTask(byte targetType, Long targetId, String hash) {
        return taskMapper.selectOne(new LambdaQueryWrapper<ContentAiTask>()
                .eq(ContentAiTask::getTaskType, TASK_TYPE_REPORT)
                .eq(ContentAiTask::getTargetType, targetType)
                .eq(ContentAiTask::getTargetId, targetId)
                .eq(ContentAiTask::getContentHash, hash)
                .eq(ContentAiTask::getDeleteState, DELETE_FALSE)
                .orderByDesc(ContentAiTask::getId)
                .last("LIMIT 1"));
    }

    private ContentReport findReporterRecord(Long userId, byte type, Long targetId, String hash) {
        return reportMapper.selectOne(new LambdaQueryWrapper<ContentReport>()
                .eq(ContentReport::getReporterUserId, userId)
                .eq(ContentReport::getTargetType, type)
                .eq(ContentReport::getTargetId, targetId)
                .eq(ContentReport::getContentHash, hash)
                .eq(ContentReport::getDeleteState, DELETE_FALSE));
    }

    private void insertReport(ContentReport report) {
        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException exception) {
            ContentReport existing = findReporterRecord(report.getReporterUserId(), report.getTargetType(),
                    report.getTargetId(), report.getContentHash());
            if (existing != null) {
                report.setId(existing.getId());
                report.setResultStatus(existing.getResultStatus());
                report.setResultMessage(existing.getResultMessage());
                return;
            }
            throw exception;
        }
    }

    // 重投/重试路径没有当前举报对象，理由改从已落库的举报单聚合
    private Map<String, Object> taskPayload(ContentAiTask task, TargetSnapshot snapshot) {
        return taskPayload(task, snapshot, aggregatedReasons(task.getTaskId()));
    }

    // 同一目标的多人举报共享一个 AI 任务，理由去重后一并给出，仅作审核视角提示
    private String aggregatedReasons(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return "";
        }
        List<ContentReport> reports = reportMapper.selectList(new LambdaQueryWrapper<ContentReport>()
                .eq(ContentReport::getTaskId, taskId)
                .eq(ContentReport::getDeleteState, DELETE_FALSE));
        return reports.stream()
                .map(ContentReport::getReason)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(MAX_REPORT_REASONS)
                .collect(Collectors.joining("；"));
    }

    private Map<String, Object> taskPayload(ContentAiTask task, TargetSnapshot snapshot, String reportReason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", task.getTaskId());
        payload.put("taskType", "CONTENT_REPORT_MODERATION");
        payload.put("reportReason", reportReason == null ? "" : reportReason);
        payload.put("targetType", targetTypeName(task.getTargetType()));
        payload.put("targetId", task.getTargetId());
        payload.put("contentHash", task.getContentHash());
        payload.put("title", snapshot.title());
        payload.put("content", snapshot.content());
        payload.put("resultDomain", "CONTENT");
        return payload;
    }

    private ContentReportVO toVO(ContentReport report) {
        ContentReportVO vo = new ContentReportVO();
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

    private byte resultStatusForTask(ContentAiTask task) {
        if (TASK_COMPLETED == safeByte(task.getStatus())) {
            return "VIOLATION".equals(task.getResultCode()) ? REPORT_SUCCESS : REPORT_REJECTED;
        }
        return TASK_FAILED == safeByte(task.getStatus()) ? REPORT_ERROR : REPORT_PROCESSING;
    }

    private static int safeRetryCount(ContentAiTask task) {
        return task.getRetryCount() == null ? 0 : task.getRetryCount();
    }

    private byte parseTargetType(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ARTICLE" -> TARGET_ARTICLE;
            case "REPLY" -> TARGET_REPLY;
            case "SUB_REPLY" -> TARGET_SUB_REPLY;
            case "DANMAKU" -> TARGET_DANMAKU;
            default -> throw new ApplicationException(Result.fail(
                    ResultCode.FAILED_PARAMS_VALIDATE, "targetType 仅允许 ARTICLE、REPLY、SUB_REPLY、DANMAKU"));
        };
    }

    private String targetTypeName(Byte value) {
        return switch (safeByte(value)) {
            case TARGET_ARTICLE -> "ARTICLE";
            case TARGET_REPLY -> "REPLY";
            case TARGET_DANMAKU -> "DANMAKU";
            default -> "SUB_REPLY";
        };
    }

    private static String summarize(String content) {
        String plain = content == null ? "" : content.replaceAll("<[^>]+>", " ").trim();
        return plain.length() > 80 ? plain.substring(0, 80) + "..." : plain;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算内容摘要", exception);
        }
    }

    private static byte safeByte(Byte value) {
        return value == null ? 0 : value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record TargetSnapshot(Long ownerUserId, String content, String title, boolean deleted) {
    }
}
