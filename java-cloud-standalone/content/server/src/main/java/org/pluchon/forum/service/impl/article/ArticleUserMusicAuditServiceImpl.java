package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.cloud.feign.ContentSystemMessageInternalFeignClient;
import org.pluchon.forum.cloud.feign.ContentWebSocketInternalFeignClient;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.mq.ForumProducer;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.ContentAiTask;
import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.mapper.ContentAiTaskMapper;
import org.pluchon.forum.mapper.UserMusicFavoriteMapper;
import org.pluchon.forum.mapper.UserMusicMapper;
import org.pluchon.forum.mapper.UserMusicPlayHistoryMapper;
import org.pluchon.forum.service.interfaces.article.ArticleMusicHotRankingService;
import org.pluchon.forum.service.interfaces.article.ArticleUserMusicAuditService;
import org.pluchon.forum.service.interfaces.article.MusicMoodTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 用户歌曲 AI 审核异步任务实现
@Slf4j
@Service
public class ArticleUserMusicAuditServiceImpl implements ArticleUserMusicAuditService {

    private static final byte TASK_TYPE_USER_MUSIC = 5;
    private static final byte TARGET_TYPE_USER_MUSIC = 2;
    private static final byte TASK_PENDING = 0;
    private static final byte TASK_COMPLETED = 2;
    private static final byte TASK_FAILED = 3;
    private static final byte DELETE_FALSE = 0;
    // 与 republishPendingTasks 的重投递上限保持一致
    private static final int MAX_TASK_RETRY = 3;

    @Autowired
    private UserMusicMapper userMusicMapper;

    @Autowired
    private ContentAiTaskMapper taskMapper;

    @Autowired
    private ForumProducer forumProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ContentSystemMessageInternalFeignClient contentSystemMessageInternalFeignClient;

    @Autowired
    private ContentWebSocketInternalFeignClient contentWebSocketInternalFeignClient;

    @Autowired
    private ArticleMusicHotRankingService articleMusicHotRankingService;

    @Autowired
    private MusicMoodTagService musicMoodTagService;

    @Autowired
    private UserMusicFavoriteMapper userMusicFavoriteMapper;

    @Autowired
    private UserMusicPlayHistoryMapper userMusicPlayHistoryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scheduleAudit(Long userMusicId) {
        if (userMusicId == null || userMusicId <= 0) {
            return;
        }
        UserMusic row = userMusicMapper.selectById(userMusicId);
        if (row == null || row.getDeleteState() != null && row.getDeleteState() == 1) {
            return;
        }
        if (row.getStatus() == null || row.getStatus() != Constant.USER_MUSIC_STATUS_REVIEWING) {
            return;
        }
        if (!StringUtils.hasText(row.getAudioUrl())) {
            return;
        }
        String contentHash = sha256(buildHashSource(row));
        ContentAiTask active = taskMapper.selectOne(new LambdaQueryWrapper<ContentAiTask>()
                .eq(ContentAiTask::getTaskType, TASK_TYPE_USER_MUSIC)
                .eq(ContentAiTask::getTargetId, row.getId())
                .eq(ContentAiTask::getContentHash, contentHash)
                .eq(ContentAiTask::getStatus, TASK_PENDING)
                .eq(ContentAiTask::getDeleteState, DELETE_FALSE)
                .last("LIMIT 1"));
        if (active != null) {
            return;
        }
        ContentAiTask task = new ContentAiTask();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setTaskType(TASK_TYPE_USER_MUSIC);
        task.setTargetType(TARGET_TYPE_USER_MUSIC);
        task.setTargetId(row.getId());
        task.setContentHash(contentHash);
        task.setTriggerUserId(row.getUserId());
        task.setStatus(TASK_PENDING);
        task.setRetryCount(0);
        task.setDeleteState(DELETE_FALSE);
        taskMapper.insert(task);
        log.info("用户歌曲 AI 审核任务已投递 userMusicId={} taskId={}", row.getId(), task.getTaskId());
        TransactionHooks.afterCommit(() -> forumProducer.sendAiAsyncTask(taskPayload(task, row)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyAsyncResult(Map<String, Object> result) {
        String taskId = text(result.get("taskId"));
        if (!StringUtils.hasText(taskId) || !"USER_MUSIC_ANALYZE".equals(text(result.get("taskType")))) {
            return;
        }
        ContentAiTask task = taskMapper.selectOne(new LambdaQueryWrapper<ContentAiTask>()
                .eq(ContentAiTask::getTaskId, taskId)
                .eq(ContentAiTask::getDeleteState, DELETE_FALSE));
        if (task == null || TASK_COMPLETED == safeByte(task.getStatus())) {
            return;
        }
        UserMusic row = userMusicMapper.selectById(task.getTargetId());
        if (row == null) {
            markTaskFailed(task, "MUSIC_NOT_FOUND", "歌曲不存在");
            return;
        }
        String currentHash = sha256(buildHashSource(row));
        if (!currentHash.equals(task.getContentHash())) {
            markTaskFailed(task, "STALE_CONTENT", "歌曲信息已变化，忽略迟到结果");
            return;
        }
        if (!"READY".equals(text(result.get("finalStatus")))) {
            applyServiceUnavailable(row, task, readMap(result.get("reviewResult")));
            return;
        }
        // AI 允许在候选集之外补新词，这里统一清洗+限量，别让模型的输出直接决定入库形状
        List<String> moodTags = MusicMoodTagServiceImpl.sanitizeTagList(readStringList(result.get("moodTags")));
        Map<String, Object> aiProfile = readMap(result.get("aiProfile"));
        Map<String, Object> reviewResult = readMap(result.get("reviewResult"));
        boolean passed = reviewResult != null && Boolean.TRUE.equals(reviewResult.get("pass"));
        byte nextStatus = passed ? Constant.USER_MUSIC_STATUS_PUBLISHED : Constant.USER_MUSIC_STATUS_REJECTED;
        if (!passed && reviewResult != null && "service_error".equals(text(reviewResult.get("kind")))) {
            applyServiceUnavailable(row, task, reviewResult);
            return;
        }
        Date now = new Date();
        userMusicMapper.update(null, new LambdaUpdateWrapper<UserMusic>()
                .eq(UserMusic::getId, row.getId())
                .eq(UserMusic::getStatus, Constant.USER_MUSIC_STATUS_REVIEWING)
                .set(UserMusic::getMoodTags, writeJson(moodTags))
                .set(UserMusic::getAiProfile, writeJson(aiProfile))
                .set(UserMusic::getReviewResult, writeJson(reviewResult))
                .set(UserMusic::getAiAnalyzedAt, now)
                .set(UserMusic::getStatus, nextStatus)
                .set(UserMusic::getUpdateTime, now));
        taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                .eq(ContentAiTask::getId, task.getId())
                .ne(ContentAiTask::getStatus, TASK_COMPLETED)
                .set(ContentAiTask::getStatus, TASK_COMPLETED)
                .set(ContentAiTask::getResultCode, passed ? "PUBLISHED" : "REJECTED")
                .set(ContentAiTask::getResultReason, null));
        if (passed) {
            // 标签只在过审后入池，未通过的歌不该把它的标签带进公共池子
            musicMoodTagService.touchAll(moodTags, MusicMoodTagServiceImpl.SOURCE_AI);
            refreshSnapshots(row);
            notifyAuditResult(row, task.getTaskId(), "PUBLISHED",
                    Constant.SYSTEM_MSG_TYPE_AUDIT_PASS,
                    Constant.SYSTEM_MSG_TITLE_MUSIC_AUDIT_PASS,
                    String.format("你的歌曲《%s》已通过审核, 已发布到音乐中心.", safeTitle(row.getTitle())));
            refreshHotScoreQuietly(row.getMusicKey());
        } else {
            String reason = reviewResult != null && StringUtils.hasText(text(reviewResult.get("reason")))
                    ? text(reviewResult.get("reason")) : "内容违规";
            notifyAuditResult(row, task.getTaskId(), "REJECTED",
                    Constant.SYSTEM_MSG_TYPE_AUDIT_FAIL,
                    Constant.SYSTEM_MSG_TITLE_MUSIC_AUDIT_FAIL,
                    String.format("你的歌曲《%s》未通过审核, 原因: %s. 请修改后重新提交.",
                            safeTitle(row.getTitle()), reason));
            removeHotScoreQuietly(row.getMusicKey());
        }
    }

    @Override
    public int republishPendingTasks() {
        Date before = new Date(System.currentTimeMillis() - 60_000L);
        List<ContentAiTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<ContentAiTask>()
                .eq(ContentAiTask::getTaskType, TASK_TYPE_USER_MUSIC)
                .eq(ContentAiTask::getStatus, TASK_PENDING)
                .eq(ContentAiTask::getDeleteState, DELETE_FALSE)
                .le(ContentAiTask::getUpdateTime, before)
                .lt(ContentAiTask::getRetryCount, MAX_TASK_RETRY)
                .last("LIMIT 20"));
        int count = 0;
        for (ContentAiTask task : tasks) {
            UserMusic row = userMusicMapper.selectById(task.getTargetId());
            if (row == null || row.getStatus() == null || row.getStatus() != Constant.USER_MUSIC_STATUS_REVIEWING) {
                continue;
            }
            forumProducer.sendAiAsyncTask(taskPayload(task, row));
            taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                    .eq(ContentAiTask::getId, task.getId())
                    .set(ContentAiTask::getRetryCount, task.getRetryCount() + 1));
            count++;
        }
        return count + giveUpExhaustedTasks(before);
    }

    /**
     * 重投递次数耗尽的任务要有终态。
     *
     * <p>否则任务永远停在 PENDING、歌永远停在 REVIEWING：这时 retryAudit 会因为
     * reviewKind 不是 service_error 而拒绝，upload 又只允许编辑 DRAFT/REJECTED，
     * 用户既重试不了也改不了。这里把它标成 service_error，让页面上现成的
     * 「重新审核」按钮可用。
     */
    private int giveUpExhaustedTasks(Date before) {
        List<ContentAiTask> exhausted = taskMapper.selectList(new LambdaQueryWrapper<ContentAiTask>()
                .eq(ContentAiTask::getTaskType, TASK_TYPE_USER_MUSIC)
                .eq(ContentAiTask::getStatus, TASK_PENDING)
                .eq(ContentAiTask::getDeleteState, DELETE_FALSE)
                .le(ContentAiTask::getUpdateTime, before)
                .ge(ContentAiTask::getRetryCount, MAX_TASK_RETRY)
                .last("LIMIT 20"));
        int count = 0;
        for (ContentAiTask task : exhausted) {
            UserMusic row = userMusicMapper.selectById(task.getTargetId());
            if (row == null || row.getStatus() == null
                    || row.getStatus() != Constant.USER_MUSIC_STATUS_REVIEWING) {
                // 歌已经有结论了，任务本身收个尾就行
                taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                        .eq(ContentAiTask::getId, task.getId())
                        .set(ContentAiTask::getStatus, TASK_FAILED)
                        .set(ContentAiTask::getResultCode, "GIVE_UP"));
                continue;
            }
            log.warn("用户歌曲审核重投递耗尽，标记为可重试 userMusicId={} taskId={}",
                    row.getId(), task.getTaskId());
            applyServiceUnavailable(row, task, null);
            count++;
        }
        return count;
    }

    /**
     * 收藏与播放历史各存了一份曲目快照，作者改名换封面后那边还是旧的。
     *
     * <p>发布是低频动作，过审时顺手刷一遍展示字段就够，不必额外挂定时任务。
     * audio_url / lrc_url 不刷——那正是快照的意义所在。
     */
    private void refreshSnapshots(UserMusic row) {
        if (row == null || !StringUtils.hasText(row.getMusicKey())) {
            return;
        }
        try {
            userMusicFavoriteMapper.refreshSnapshot(row.getMusicKey(), row.getTitle(),
                    row.getArtist(), row.getAlbum(), row.getCoverUrl());
            userMusicPlayHistoryMapper.refreshSnapshot(row.getMusicKey(), row.getTitle(),
                    row.getArtist(), row.getAlbum(), row.getCoverUrl());
        } catch (Exception e) {
            // 快照是展示用的冗余，刷不动不该把审核结果一起回滚
            log.warn("刷新曲目快照失败 musicKey={}", row.getMusicKey(), e);
        }
    }

    private Map<String, Object> taskPayload(ContentAiTask task, UserMusic row) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", task.getTaskId());
        payload.put("taskType", "USER_MUSIC_ANALYZE");
        payload.put("resultDomain", "CONTENT");
        payload.put("targetType", "USER_MUSIC");
        payload.put("targetId", row.getId());
        payload.put("contentHash", task.getContentHash());
        payload.put("musicKey", row.getMusicKey());
        payload.put("title", row.getTitle());
        payload.put("artist", row.getArtist());
        payload.put("lyricText", row.getLyricText());
        payload.put("audioUrl", row.getAudioUrl());
        payload.put("userMoodTags", parseUserMoodTags(row.getMoodTags()));
        return payload;
    }

    private List<String> parseUserMoodTags(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> tags = objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
            return tags == null ? List.of() : tags;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String buildHashSource(UserMusic row) {
        return text(row.getTitle()) + "\n"
                + text(row.getArtist()) + "\n"
                + text(row.getAlbum()) + "\n"
                + text(row.getAudioUrl()) + "\n"
                + text(row.getLyricText()) + "\n"
                + text(row.getMoodTags());
    }

    private void applyServiceUnavailable(UserMusic row, ContentAiTask task, Map<String, Object> reviewResult) {
        Map<String, Object> stored = new HashMap<>(reviewResult == null ? Map.of() : reviewResult);
        stored.put("pass", false);
        stored.put("kind", "service_error");
        stored.put("reason", "内部错误，稍后进行重试");
        Date now = new Date();
        userMusicMapper.update(null, new LambdaUpdateWrapper<UserMusic>()
                .eq(UserMusic::getId, row.getId())
                .eq(UserMusic::getStatus, Constant.USER_MUSIC_STATUS_REVIEWING)
                .set(UserMusic::getReviewResult, writeJson(stored))
                .set(UserMusic::getUpdateTime, now));
        taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                .eq(ContentAiTask::getId, task.getId())
                .ne(ContentAiTask::getStatus, TASK_COMPLETED)
                .set(ContentAiTask::getStatus, TASK_COMPLETED)
                .set(ContentAiTask::getResultCode, "SERVICE_UNAVAILABLE")
                .set(ContentAiTask::getResultReason, "内部错误，稍后进行重试"));
        notifyAuditResult(row, task.getTaskId(), "SERVICE_UNAVAILABLE",
                Constant.SYSTEM_MSG_TYPE_AUDIT_ERROR,
                Constant.SYSTEM_MSG_TITLE_MUSIC_AUDIT_ERROR,
                String.format("你的歌曲《%s》审核异常: 内部错误，稍后进行重试. 请稍后重新提交.",
                        safeTitle(row.getTitle())));
    }

    private void notifyAuditResult(UserMusic row, String taskId, String finalStatus,
                                   Byte sysMsgType, String title, String content) {
        Long userId = row.getUserId();
        Long musicId = row.getId();
        Long systemMsgId = null;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("kind", "music");
            payload.put("targetType", "music");
            payload.put("musicId", musicId);
            payload.put("taskId", taskId);
            payload.put("finalStatus", finalStatus);
            String payloadJson;
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (Exception e) {
                payloadJson = null;
            }
            systemMsgId = contentSystemMessageInternalFeignClient.createMessage(
                    userId, sysMsgType, title, truncate(content, 500), musicId, payloadJson);
        } catch (Exception e) {
            log.error("写入歌曲审核系统消息失败: userId={}, musicId={}", userId, musicId, e);
        }
        pushAuditRealtimeNotify(userId, musicId, taskId, finalStatus, title, content, systemMsgId);
    }

    private void pushAuditRealtimeNotify(Long userId, Long musicId, String taskId, String finalStatus,
                                         String title, String content, Long systemMsgId) {
        if (userId == null) {
            return;
        }
        try {
            Map<String, Object> auditWs = new LinkedHashMap<>();
            auditWs.put("type", "audit_result");
            auditWs.put("kind", "music");
            auditWs.put("targetType", "music");
            auditWs.put("musicId", musicId);
            auditWs.put("taskId", taskId);
            auditWs.put("finalStatus", finalStatus);
            auditWs.put("resultMessage", truncate(content, 500));
            auditWs.put("title", title);
            contentWebSocketInternalFeignClient.push(userId, objectMapper.writeValueAsString(auditWs));
            Map<String, Object> sysWs = new LinkedHashMap<>();
            sysWs.put("type", "system_message");
            sysWs.put("kind", "music");
            sysWs.put("targetType", "music");
            sysWs.put("messageId", systemMsgId);
            sysWs.put("musicId", musicId);
            sysWs.put("title", title);
            sysWs.put("content", truncate(content, 500));
            sysWs.put("finalStatus", finalStatus);
            contentWebSocketInternalFeignClient.push(userId, objectMapper.writeValueAsString(sysWs));
        } catch (Exception e) {
            log.warn("歌曲审核结果 WebSocket 推送失败 userId={}, musicId={}", userId, musicId, e);
        }
    }

    private static String safeTitle(String title) {
        String value = text(title);
        return StringUtils.hasText(value) ? value : "未命名歌曲";
    }

    private void markTaskFailed(ContentAiTask task, String code, String reason) {
        taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                .eq(ContentAiTask::getId, task.getId())
                .set(ContentAiTask::getStatus, TASK_FAILED)
                .set(ContentAiTask::getResultCode, code)
                .set(ContentAiTask::getResultReason, truncate(reason, 500)));
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            log.warn("歌曲 AI 字段序列化失败 error={}", exception.getMessage());
            return null;
        }
    }

    private List<String> readStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                String text = text(item);
                if (StringUtils.hasText(text)) {
                    out.add(text);
                }
            }
            return out;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return out;
        }
        if (value instanceof String raw && StringUtils.hasText(raw)) {
            try {
                return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private static byte safeByte(Byte value) {
        return value == null ? 0 : value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算歌曲哈希", exception);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void refreshHotScoreQuietly(String musicKey) {
        try {
            articleMusicHotRankingService.refreshTrackScore(musicKey);
        } catch (Exception ignored) {
            // 热榜刷新失败不影响审核主流程
        }
    }

    private void removeHotScoreQuietly(String musicKey) {
        try {
            articleMusicHotRankingService.removeFromRanking(musicKey);
        } catch (Exception ignored) {
            // 热榜移除失败不影响审核主流程
        }
    }

    private static String truncate(String value, int maxLength) {
        String text = text(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
