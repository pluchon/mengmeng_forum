package org.pluchon.forum.service.impl.moderation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.pluchon.forum.common.mq.ForumProducer;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.ArticleReply;
import org.pluchon.forum.entity.db.ArticleSubReply;
import org.pluchon.forum.entity.db.ArticleVideoDanmaku;
import org.pluchon.forum.entity.db.ContentAiTask;
import org.pluchon.forum.mapper.ArticleReplyMapper;
import org.pluchon.forum.mapper.ArticleSubReplyMapper;
import org.pluchon.forum.mapper.ArticleVideoDanmakuMapper;
import org.pluchon.forum.mapper.ContentAiTaskMapper;
import org.pluchon.forum.service.interfaces.article.ArticleQuestionService;
import org.pluchon.forum.service.interfaces.article.ArticleService;
import org.pluchon.forum.service.interfaces.moderation.ContentModerationTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 评论先发布后审核的领域实现
@Service
public class ContentModerationTaskServiceImpl implements ContentModerationTaskService {

    private static final byte TASK_TYPE_COMMENT = 2;
    private static final byte TASK_TYPE_DANMAKU = 4;
    private static final byte TARGET_REPLY = 2;
    private static final byte TARGET_SUB_REPLY = 3;
    private static final byte TARGET_DANMAKU = 4;
    private static final byte TASK_PENDING = 0;
    private static final byte TASK_COMPLETED = 2;
    private static final byte TASK_FAILED = 3;
    private static final byte DELETE_FALSE = 0;
    private static final byte DELETE_TRUE = 1;

    @Autowired
    private ContentAiTaskMapper taskMapper;

    @Autowired
    private ArticleReplyMapper articleReplyMapper;

    @Autowired
    private ArticleSubReplyMapper articleSubReplyMapper;

    @Autowired
    private ArticleVideoDanmakuMapper articleVideoDanmakuMapper;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleQuestionService articleQuestionService;

    @Autowired
    private ForumProducer forumProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scheduleComment(Byte targetType, Long targetId, String content) {
        String plain = stripHtml(content);
        if (!StringUtils.hasText(plain) || targetId == null || targetType == null
                || (TARGET_REPLY != targetType && TARGET_SUB_REPLY != targetType)) {
            return;
        }
        ContentAiTask task = new ContentAiTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskType(TASK_TYPE_COMMENT);
        task.setTargetType(targetType);
        task.setTargetId(targetId);
        task.setContentHash(sha256(content));
        task.setStatus(TASK_PENDING);
        task.setRetryCount(0);
        task.setDeleteState(DELETE_FALSE);
        taskMapper.insert(task);
        Map<String, Object> payload = taskPayload(task, content);
        TransactionHooks.afterCommit(() -> forumProducer.sendAiAsyncTask(payload));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scheduleDanmaku(Long targetId, String content) {
        if (!StringUtils.hasText(content) || targetId == null) {
            return;
        }
        ContentAiTask task = new ContentAiTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskType(TASK_TYPE_DANMAKU);
        task.setTargetType(TARGET_DANMAKU);
        task.setTargetId(targetId);
        task.setContentHash(sha256(content));
        task.setStatus(TASK_PENDING);
        task.setRetryCount(0);
        task.setDeleteState(DELETE_FALSE);
        taskMapper.insert(task);
        Map<String, Object> payload = taskPayload(task, content);
        TransactionHooks.afterCommit(() -> forumProducer.sendAiAsyncTask(payload));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyAsyncResult(Map<String, Object> result) {
        String resultTaskType = text(result.get("taskType"));
        if (!"COMMENT_AUTO_MODERATION".equals(resultTaskType)
                && !"DANMAKU_AUTO_MODERATION".equals(resultTaskType)) {
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
            handleError(task, text(result.get("finalReason")));
            return;
        }
        if ("VIOLATION".equals(finalStatus)) {
            deleteConfirmedViolation(task.getTargetType(), task.getTargetId(), task.getContentHash());
        }
        taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                .eq(ContentAiTask::getId, task.getId())
                .set(ContentAiTask::getStatus, TASK_COMPLETED)
                .set(ContentAiTask::getResultCode, finalStatus)
                .set(ContentAiTask::getResultReason, text(result.get("finalReason"))));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int republishPendingTasks() {
        Date before = new Date(System.currentTimeMillis() - 60_000L);
        List<ContentAiTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<ContentAiTask>()
                .in(ContentAiTask::getTaskType, TASK_TYPE_COMMENT, TASK_TYPE_DANMAKU)
                .eq(ContentAiTask::getStatus, TASK_PENDING)
                .eq(ContentAiTask::getDeleteState, DELETE_FALSE)
                .le(ContentAiTask::getUpdateTime, before)
                .lt(ContentAiTask::getRetryCount, 3)
                .last("LIMIT 20"));
        int count = 0;
        for (ContentAiTask task : tasks) {
            String content = loadCurrentContent(task);
            if (!StringUtils.hasText(content) || !task.getContentHash().equals(sha256(content))) {
                continue;
            }
            forumProducer.sendAiAsyncTask(taskPayload(task, content));
            taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                    .eq(ContentAiTask::getId, task.getId())
                    .set(ContentAiTask::getRetryCount, safeRetryCount(task) + 1));
            count++;
        }
        return count;
    }

    private void handleError(ContentAiTask task, String reason) {
        int retries = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retries >= 2) {
            taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                    .eq(ContentAiTask::getId, task.getId())
                    .set(ContentAiTask::getStatus, TASK_FAILED)
                    .set(ContentAiTask::getRetryCount, retries + 1)
                    .set(ContentAiTask::getResultCode, "ERROR")
                    .set(ContentAiTask::getResultReason, reason));
            return;
        }
        String content = loadCurrentContent(task);
        task.setRetryCount(retries + 1);
        taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                .eq(ContentAiTask::getId, task.getId())
                .set(ContentAiTask::getStatus, TASK_PENDING)
                .set(ContentAiTask::getRetryCount, task.getRetryCount())
                .set(ContentAiTask::getResultReason, reason));
        TransactionHooks.afterCommit(() -> forumProducer.sendAiAsyncTask(taskPayload(task, content)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteConfirmedViolation(Byte targetType, Long targetId, String contentHash) {
        if (TARGET_DANMAKU == safeByte(targetType)) {
            ArticleVideoDanmaku danmaku = articleVideoDanmakuMapper.selectById(targetId);
            if (danmaku == null || DELETE_TRUE == safeByte(danmaku.getDeleteState())
                    || !contentHash.equals(sha256(danmaku.getContent()))) {
                return false;
            }
            return articleVideoDanmakuMapper.update(null, new LambdaUpdateWrapper<ArticleVideoDanmaku>()
                    .eq(ArticleVideoDanmaku::getId, danmaku.getId())
                    .ne(ArticleVideoDanmaku::getDeleteState, DELETE_TRUE)
                    .set(ArticleVideoDanmaku::getDeleteState, DELETE_TRUE)) > 0;
        }
        if (TARGET_REPLY == safeByte(targetType)) {
            ArticleReply reply = articleReplyMapper.selectById(targetId);
            if (reply == null || DELETE_TRUE == safeByte(reply.getDeleteState())
                    || !contentHash.equals(sha256(reply.getContent()))) {
                return false;
            }
            List<ArticleSubReply> subReplies = articleSubReplyMapper.selectList(
                    new LambdaQueryWrapper<ArticleSubReply>()
                            .eq(ArticleSubReply::getReplyId, reply.getId())
                            .ne(ArticleSubReply::getDeleteState, DELETE_TRUE));
            articleSubReplyMapper.update(null, new LambdaUpdateWrapper<ArticleSubReply>()
                    .eq(ArticleSubReply::getReplyId, reply.getId())
                    .ne(ArticleSubReply::getDeleteState, DELETE_TRUE)
                    .set(ArticleSubReply::getDeleteState, DELETE_TRUE));
            articleReplyMapper.update(null, new LambdaUpdateWrapper<ArticleReply>()
                    .eq(ArticleReply::getId, reply.getId())
                    .ne(ArticleReply::getDeleteState, DELETE_TRUE)
                    .set(ArticleReply::getDeleteState, DELETE_TRUE));
            articleQuestionService.handleDeletedReply(reply.getArticleId(), reply.getId());
            articleService.deleteReply(reply.getArticleId());
            for (ArticleSubReply ignored : subReplies) {
                articleService.deleteSubReply(reply.getArticleId());
            }
            return true;
        }
        ArticleSubReply subReply = articleSubReplyMapper.selectById(targetId);
        if (subReply == null || DELETE_TRUE == safeByte(subReply.getDeleteState())
                || !contentHash.equals(sha256(subReply.getContent()))) {
            return false;
        }
        int updated = articleSubReplyMapper.update(null, new LambdaUpdateWrapper<ArticleSubReply>()
                .eq(ArticleSubReply::getId, subReply.getId())
                .ne(ArticleSubReply::getDeleteState, DELETE_TRUE)
                .set(ArticleSubReply::getDeleteState, DELETE_TRUE));
        if (updated > 0) {
            articleQuestionService.handleDeletedSubReply(subReply.getArticleId(), subReply.getId());
            articleService.deleteSubReply(subReply.getArticleId());
        }
        return updated > 0;
    }

    private String loadCurrentContent(ContentAiTask task) {
        if (TARGET_DANMAKU == safeByte(task.getTargetType())) {
            ArticleVideoDanmaku danmaku = articleVideoDanmakuMapper.selectById(task.getTargetId());
            return danmaku == null ? "" : danmaku.getContent();
        }
        if (TARGET_REPLY == safeByte(task.getTargetType())) {
            ArticleReply reply = articleReplyMapper.selectById(task.getTargetId());
            return reply == null ? "" : reply.getContent();
        }
        ArticleSubReply subReply = articleSubReplyMapper.selectById(task.getTargetId());
        return subReply == null ? "" : subReply.getContent();
    }

    private Map<String, Object> taskPayload(ContentAiTask task, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", task.getTaskId());
        boolean danmakuTask = TASK_TYPE_DANMAKU == safeByte(task.getTaskType());
        payload.put("taskType", danmakuTask ? "DANMAKU_AUTO_MODERATION" : "COMMENT_AUTO_MODERATION");
        payload.put("targetType", danmakuTask
                ? "DANMAKU"
                : (TARGET_REPLY == safeByte(task.getTargetType()) ? "REPLY" : "SUB_REPLY"));
        payload.put("targetId", task.getTargetId());
        payload.put("contentHash", task.getContentHash());
        payload.put("content", content);
        payload.put("resultDomain", "CONTENT");
        return payload;
    }

    private static String stripHtml(String content) {
        return content == null ? "" : content.replaceAll("<[^>]+>", " ").trim();
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

    private static int safeRetryCount(ContentAiTask task) {
        return task.getRetryCount() == null ? 0 : task.getRetryCount();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
