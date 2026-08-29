package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.service.remote.ContentAiGatewayService;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.mq.ForumProducer;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.ContentAiTask;
import org.pluchon.forum.entity.db.ForumArticleAiFeature;
import org.pluchon.forum.entity.dto.RagArticleIndexDTO;
import org.pluchon.forum.entity.vo.article.ArticleSummaryVO;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.mapper.ContentAiTaskMapper;
import org.pluchon.forum.mapper.ForumArticleAiFeatureMapper;
import org.pluchon.forum.service.interfaces.article.ArticleSummaryService;
import org.pluchon.forum.service.interfaces.article.ArticleTagService;
import org.pluchon.forum.service.impl.remote.ContentUserLookupService;
import org.pluchon.forum.service.interfaces.search.ArticleSearchIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import java.util.concurrent.TimeUnit;

// 帖子AI总结异步任务实现
@Slf4j
@Service
public class ArticleSummaryServiceImpl implements ArticleSummaryService {

    private static final byte SUMMARY_NOT_READY = 0;
    private static final byte SUMMARY_PROCESSING = 1;
    private static final byte SUMMARY_READY = 2;
    private static final byte SUMMARY_FAILED = 3;
    private static final byte SUMMARY_TOO_SHORT = 4;
    private static final byte TASK_TYPE_SUMMARY = 1;
    private static final byte TARGET_TYPE_ARTICLE = 1;
    private static final byte TASK_PENDING = 0;
    // 补投上限；到达后转 FAILED 终态，由用户手动「重新生成」
    private static final int MAX_SUMMARY_RETRY = 3;
    private static final byte TASK_COMPLETED = 2;
    private static final byte TASK_FAILED = 3;
    private static final byte DELETE_FALSE = 0;
    private static final long MANUAL_COOLDOWN_MILLIS = 10L * 60L * 1000L;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ForumArticleAiFeatureMapper featureMapper;

    @Autowired
    private ContentAiTaskMapper taskMapper;

    @Autowired
    private ForumProducer forumProducer;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ArticleSearchIndexService articleSearchIndexService;

    @Autowired
    private ContentAiGatewayService aiHubService;

    @Autowired
    private ContentUserLookupService userService;

    @Autowired
    private ArticleTagService articleTagService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scheduleInitialSummary(Long articleId) {
        Article article = requirePublishedArticle(articleId);
        String plain = plainText(article.getContent());
        String contentHash = sha256(article.getTitle() + "\n" + article.getContent());
        ForumArticleAiFeature feature = getOrCreateFeature(article, contentHash);
        if (plain.length() <= 50) {
            featureMapper.update(null, new LambdaUpdateWrapper<ForumArticleAiFeature>()
                    .eq(ForumArticleAiFeature::getId, feature.getId())
                    .set(ForumArticleAiFeature::getSummaryText, null)
                    .set(ForumArticleAiFeature::getSummaryStatus, SUMMARY_TOO_SHORT)
                    .set(ForumArticleAiFeature::getSummaryContentHash, contentHash)
                    .set(ForumArticleAiFeature::getSummaryGeneratedAt, new Date()));
            return;
        }
        if (SUMMARY_PROCESSING == safeByte(feature.getSummaryStatus())
                && contentHash.equals(feature.getSummaryContentHash())) {
            return;
        }
        createSummaryTask(article, feature, contentHash, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleSummaryVO getSummary(Long articleId) {
        Article article = requirePublishedArticle(articleId);
        String contentHash = sha256(article.getTitle() + "\n" + article.getContent());
        ForumArticleAiFeature feature = featureMapper.selectOne(new LambdaQueryWrapper<ForumArticleAiFeature>()
                .eq(ForumArticleAiFeature::getArticleId, articleId)
                .eq(ForumArticleAiFeature::getDeleteState, DELETE_FALSE));
        if (feature == null || !contentHash.equals(feature.getSummaryContentHash())) {
            scheduleInitialSummary(articleId);
            feature = featureMapper.selectOne(new LambdaQueryWrapper<ForumArticleAiFeature>()
                    .eq(ForumArticleAiFeature::getArticleId, articleId)
                    .eq(ForumArticleAiFeature::getDeleteState, DELETE_FALSE));
        }
        return toVO(feature, articleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleSummaryVO regenerate(Long articleId, Long loginUserId) {
        if (loginUserId == null || loginUserId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        Article article = requirePublishedArticle(articleId);
        Date cooldownStart = new Date(System.currentTimeMillis() - MANUAL_COOLDOWN_MILLIS);
        ContentAiTask recent = taskMapper.selectOne(new LambdaQueryWrapper<ContentAiTask>()
                .eq(ContentAiTask::getTaskType, TASK_TYPE_SUMMARY)
                .eq(ContentAiTask::getTargetId, articleId)
                .isNotNull(ContentAiTask::getTriggerUserId)
                .eq(ContentAiTask::getDeleteState, DELETE_FALSE)
                .ge(ContentAiTask::getCreateTime, cooldownStart)
                .orderByDesc(ContentAiTask::getId)
                .last("LIMIT 1"));
        if (recent != null) {
            ArticleSummaryVO cooling = toVO(getFeature(articleId), articleId);
            cooling.setCooldownHit(true);
            return cooling;
        }
        String plain = plainText(article.getContent());
        if (plain.length() <= 50) {
            scheduleInitialSummary(articleId);
            return toVO(getFeature(articleId), articleId);
        }
        String contentHash = sha256(article.getTitle() + "\n" + article.getContent());
        ForumArticleAiFeature feature = getOrCreateFeature(article, contentHash);
        createSummaryTask(article, feature, contentHash, loginUserId);
        return toVO(getFeature(articleId), articleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyAsyncResult(Map<String, Object> result) {
        String taskId = text(result.get("taskId"));
        if (!StringUtils.hasText(taskId) || !"ARTICLE_SUMMARY".equals(text(result.get("taskType")))) {
            return;
        }
        ContentAiTask task = taskMapper.selectOne(new LambdaQueryWrapper<ContentAiTask>()
                .eq(ContentAiTask::getTaskId, taskId)
                .eq(ContentAiTask::getDeleteState, DELETE_FALSE));
        if (task == null || TASK_COMPLETED == safeByte(task.getStatus())) {
            return;
        }
        Article article = articleMapper.selectById(task.getTargetId());
        if (article == null) {
            markTaskFailed(task, "ARTICLE_NOT_FOUND", "帖子不存在");
            return;
        }
        String currentHash = sha256(article.getTitle() + "\n" + article.getContent());
        if (!currentHash.equals(task.getContentHash())) {
            markTaskFailed(task, "STALE_CONTENT", "正文已变化，忽略迟到结果");
            return;
        }
        String finalStatus = text(result.get("finalStatus"));
        String summary = text(result.get("summary"));
        if (!"READY".equals(finalStatus) || !StringUtils.hasText(summary)) {
            markTaskFailed(task, "AI_SUMMARY_FAILED", text(result.get("finalReason")));
            featureMapper.update(null, new LambdaUpdateWrapper<ForumArticleAiFeature>()
                    .eq(ForumArticleAiFeature::getArticleId, article.getId())
                    .eq(ForumArticleAiFeature::getSummaryContentHash, currentHash)
                    .set(ForumArticleAiFeature::getSummaryStatus, SUMMARY_FAILED));
            return;
        }
        Date now = new Date();
        featureMapper.update(null, new LambdaUpdateWrapper<ForumArticleAiFeature>()
                .eq(ForumArticleAiFeature::getArticleId, article.getId())
                .set(ForumArticleAiFeature::getSummaryText, summary)
                .set(ForumArticleAiFeature::getSummaryStatus, SUMMARY_READY)
                .set(ForumArticleAiFeature::getSummaryContentHash, currentHash)
                .set(ForumArticleAiFeature::getSummaryGeneratedAt, now));
        taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                .eq(ContentAiTask::getId, task.getId())
                .ne(ContentAiTask::getStatus, TASK_COMPLETED)
                .set(ContentAiTask::getStatus, TASK_COMPLETED)
                .set(ContentAiTask::getResultCode, "READY")
                .set(ContentAiTask::getResultReason, null));
        stringRedisTemplate.opsForValue().set(cacheKey(article.getId(), currentHash), summary, 24, TimeUnit.HOURS);
        TransactionHooks.afterCommit(() -> {
            articleSearchIndexService.syncPublishedArticle(article.getId());
            refreshRagIndex(article, summary);
        });
    }

    @Override
    public int republishPendingTasks() {
        Date before = new Date(System.currentTimeMillis() - 60_000L);
        // 先给补投耗尽的任务收口。Python 侧异常时是 basic_nack(requeue=false)，
        // 消息直接丢弃、Java 永远等不到结果，任务就一直停在 PENDING；
        // 补投上限用尽后没人再管，帖子的 summaryStatus 会永远是 PROCESSING，
        // 前端也就一直轮询转圈。这里把它推到 FAILED —— 前端拿到终态会停止
        // 轮询并露出「重新生成」入口
        expireExhaustedTasks(before);
        List<ContentAiTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<ContentAiTask>()
                .eq(ContentAiTask::getTaskType, TASK_TYPE_SUMMARY)
                .eq(ContentAiTask::getStatus, TASK_PENDING)
                .eq(ContentAiTask::getDeleteState, DELETE_FALSE)
                .le(ContentAiTask::getUpdateTime, before)
                .lt(ContentAiTask::getRetryCount, MAX_SUMMARY_RETRY)
                .last("LIMIT 20"));
        int count = 0;
        for (ContentAiTask task : tasks) {
            Article article = articleMapper.selectById(task.getTargetId());
            if (article == null) {
                continue;
            }
            forumProducer.sendAiAsyncTask(taskPayload(task, article));
            taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                    .eq(ContentAiTask::getId, task.getId())
                    .set(ContentAiTask::getRetryCount, task.getRetryCount() + 1));
            count++;
        }
        return count;
    }

    // 补投次数已用尽却仍无结果的任务，标记为失败终态
    private void expireExhaustedTasks(Date before) {
        List<ContentAiTask> exhausted = taskMapper.selectList(new LambdaQueryWrapper<ContentAiTask>()
                .eq(ContentAiTask::getTaskType, TASK_TYPE_SUMMARY)
                .eq(ContentAiTask::getStatus, TASK_PENDING)
                .eq(ContentAiTask::getDeleteState, DELETE_FALSE)
                .le(ContentAiTask::getUpdateTime, before)
                .ge(ContentAiTask::getRetryCount, MAX_SUMMARY_RETRY)
                .last("LIMIT 20"));
        for (ContentAiTask task : exhausted) {
            markTaskFailed(task, "REPUBLISH_EXHAUSTED",
                    "补投 " + MAX_SUMMARY_RETRY + " 次仍未收到 AI 结果，具体原因见 ai-server 日志 task_id="
                            + task.getTaskId());
            featureMapper.update(null, new LambdaUpdateWrapper<ForumArticleAiFeature>()
                    .eq(ForumArticleAiFeature::getArticleId, task.getTargetId())
                    .eq(ForumArticleAiFeature::getSummaryContentHash, task.getContentHash())
                    .set(ForumArticleAiFeature::getSummaryStatus, SUMMARY_FAILED));
            log.warn("帖子总结补投耗尽，标记为失败 articleId={} taskId={}",
                    task.getTargetId(), task.getTaskId());
        }
    }

    private void createSummaryTask(Article article, ForumArticleAiFeature feature, String contentHash,
            Long triggerUserId) {
        ContentAiTask active = taskMapper.selectOne(new LambdaQueryWrapper<ContentAiTask>()
                .eq(ContentAiTask::getTaskType, TASK_TYPE_SUMMARY)
                .eq(ContentAiTask::getTargetId, article.getId())
                .eq(ContentAiTask::getContentHash, contentHash)
                .eq(ContentAiTask::getStatus, TASK_PENDING)
                .eq(ContentAiTask::getDeleteState, DELETE_FALSE)
                .last("LIMIT 1"));
        if (active != null) {
            return;
        }
        ContentAiTask task = new ContentAiTask();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setTaskType(TASK_TYPE_SUMMARY);
        task.setTargetType(TARGET_TYPE_ARTICLE);
        task.setTargetId(article.getId());
        task.setContentHash(contentHash);
        task.setTriggerUserId(triggerUserId);
        task.setStatus(TASK_PENDING);
        task.setRetryCount(0);
        task.setDeleteState(DELETE_FALSE);
        taskMapper.insert(task);
        featureMapper.update(null, new LambdaUpdateWrapper<ForumArticleAiFeature>()
                .eq(ForumArticleAiFeature::getId, feature.getId())
                .set(ForumArticleAiFeature::getSummaryStatus, SUMMARY_PROCESSING)
                .set(ForumArticleAiFeature::getSummaryContentHash, contentHash));
        TransactionHooks.afterCommit(() -> forumProducer.sendAiAsyncTask(taskPayload(task, article)));
    }

    private Map<String, Object> taskPayload(ContentAiTask task, Article article) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", task.getTaskId());
        payload.put("taskType", "ARTICLE_SUMMARY");
        payload.put("resultDomain", "CONTENT");
        payload.put("targetType", "ARTICLE");
        payload.put("targetId", article.getId());
        payload.put("contentHash", task.getContentHash());
        payload.put("title", article.getTitle());
        payload.put("content", article.getContent());
        return payload;
    }

    private void refreshRagIndex(Article article, String summary) {
        try {
            UserInternalVO author = userService.getUserInfoById(article.getUserId());
            RagArticleIndexDTO payload = new RagArticleIndexDTO();
            payload.setArticleId(article.getId());
            payload.setTitle(article.getTitle());
            payload.setContent(article.getContent());
            payload.setMediaType(article.getMediaType() == null ? 0 : article.getMediaType().intValue());
            payload.setVideoUrl(article.getVideoUrl());
            payload.setCoverUrl(article.getCoverImg());
            payload.setSummary(summary);
            payload.setAuthorNickname(author == null ? "" : author.getNickname());
            payload.setTagNames(articleTagService.tagNamesByArticleId(article.getId()));
            aiHubService.indexArticleRag(payload);
        } catch (Exception exception) {
            log.warn("帖子总结完成后RAG索引刷新失败 articleId={} error={}",
                    article.getId(), exception.getMessage());
        }
    }

    private ForumArticleAiFeature getOrCreateFeature(Article article, String contentHash) {
        ForumArticleAiFeature feature = getFeature(article.getId());
        if (feature != null) {
            return feature;
        }
        feature = new ForumArticleAiFeature();
        feature.setArticleId(article.getId());
        feature.setFeatureJson("{}");
        feature.setSummaryStatus(SUMMARY_NOT_READY);
        feature.setSummaryContentHash(contentHash);
        feature.setFeatureVersion("v1");
        feature.setContentHash(contentHash);
        feature.setGeneratedBy("SUMMARY_PENDING");
        feature.setDeleteState(DELETE_FALSE);
        featureMapper.insert(feature);
        return feature;
    }

    private ForumArticleAiFeature getFeature(Long articleId) {
        return featureMapper.selectOne(new LambdaQueryWrapper<ForumArticleAiFeature>()
                .eq(ForumArticleAiFeature::getArticleId, articleId)
                .eq(ForumArticleAiFeature::getDeleteState, DELETE_FALSE));
    }

    private Article requirePublishedArticle(Long articleId) {
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .eq(Article::getDeleteState, DELETE_FALSE));
        if (article == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_ARTICLE));
        }
        return article;
    }

    private ArticleSummaryVO toVO(ForumArticleAiFeature feature, Long articleId) {
        ArticleSummaryVO vo = new ArticleSummaryVO();
        byte status = feature == null ? SUMMARY_NOT_READY : safeByte(feature.getSummaryStatus());
        vo.setStatus(statusName(status));
        vo.setSummary(feature == null ? null : feature.getSummaryText());
        vo.setCanExpand(status == SUMMARY_READY || status == SUMMARY_TOO_SHORT
                || (status == SUMMARY_PROCESSING && StringUtils.hasText(vo.getSummary())));
        ContentAiTask recent = taskMapper.selectOne(new LambdaQueryWrapper<ContentAiTask>()
                .eq(ContentAiTask::getTaskType, TASK_TYPE_SUMMARY)
                .eq(ContentAiTask::getTargetId, articleId)
                .isNotNull(ContentAiTask::getTriggerUserId)
                .eq(ContentAiTask::getDeleteState, DELETE_FALSE)
                .orderByDesc(ContentAiTask::getId)
                .last("LIMIT 1"));
        long retryAfter = 0;
        if (recent != null && recent.getCreateTime() != null) {
            retryAfter = Math.max(0L,
                    (recent.getCreateTime().getTime() + MANUAL_COOLDOWN_MILLIS - System.currentTimeMillis()) / 1000L);
        }
        vo.setCanRegenerate(status != SUMMARY_PROCESSING && retryAfter == 0);
        if (status == SUMMARY_TOO_SHORT) {
            vo.setSummary("当前帖子内容较少，建议包含更多内容后再尝试 AI 智能总结。");
        }
        return vo;
    }

    private void markTaskFailed(ContentAiTask task, String code, String reason) {
        taskMapper.update(null, new LambdaUpdateWrapper<ContentAiTask>()
                .eq(ContentAiTask::getId, task.getId())
                .set(ContentAiTask::getStatus, TASK_FAILED)
                .set(ContentAiTask::getResultCode, code)
                .set(ContentAiTask::getResultReason, truncate(reason, 500)));
    }

    private static String cacheKey(Long articleId, String hash) {
        return "article_summary:" + articleId + ":" + hash;
    }

    private static String statusName(byte status) {
        return switch (status) {
            case SUMMARY_PROCESSING -> "PROCESSING";
            case SUMMARY_READY -> "READY";
            case SUMMARY_FAILED -> "FAILED";
            case SUMMARY_TOO_SHORT -> "TOO_SHORT";
            default -> "NOT_READY";
        };
    }

    private static byte safeByte(Byte value) {
        return value == null ? SUMMARY_NOT_READY : value;
    }

    private static String plainText(String content) {
        return text(content).replaceAll("(?s)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?s)<style[^>]*>.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
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
            throw new IllegalStateException("无法计算正文哈希", exception);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String truncate(String value, int maxLength) {
        String text = text(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
