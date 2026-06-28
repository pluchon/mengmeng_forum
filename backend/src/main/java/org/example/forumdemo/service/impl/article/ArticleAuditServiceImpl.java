package org.example.forumdemo.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.mq.ForumProducer;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.MailUtil;
import org.example.forumdemo.common.utils.PiiUtils;
import org.example.forumdemo.common.utils.RequestIpUtils;
import org.example.forumdemo.common.utils.TransactionHooks;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.dto.ai.RagArticleIndexDTO;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.article.AuditStatusResponse;
import org.example.forumdemo.entity.vo.mq.ArticleAuditResultMqVO;
import org.example.forumdemo.entity.vo.mq.ArticleAuditTaskMqVO;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.service.impl.article.auditguard.ArticleAuditSubmitContext;
import org.example.forumdemo.service.impl.article.auditguard.ArticleAuditSubmitGuardChain;
import org.example.forumdemo.service.impl.article.auditguard.ArticleAuditSubmitGuardResult;
import org.example.forumdemo.service.impl.websocket.WebSocketPushService;
import org.example.forumdemo.service.interfaces.ai.AiHubService;
import org.example.forumdemo.service.interfaces.article.ArticleAuditService;
import org.example.forumdemo.service.interfaces.article.ArticleHotRankingService;
import org.example.forumdemo.service.interfaces.article.ArticleMediaService;
import org.example.forumdemo.service.interfaces.article.ArticlePublishSideEffectService;
import org.example.forumdemo.service.interfaces.article.ArticleTagService;
import org.example.forumdemo.service.interfaces.board.BoardService;
import org.example.forumdemo.service.interfaces.common.IpRegionService;
import org.example.forumdemo.service.interfaces.message.SystemMessageService;
import org.example.forumdemo.service.interfaces.search.ArticleSearchIndexService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// 帖子异步审核提交、回执应用与超时兜底
@Service
@Slf4j
public class ArticleAuditServiceImpl implements ArticleAuditService {

    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_FORBIDDEN = 1;
    private static final byte AUDIT_NOTIFY_EMAIL_ON = 1;
    private static final byte AUDIT_NOTIFY_EMAIL_OFF = 0;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private BoardService boardService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ForumProducer forumProducer;

    @Autowired
    private SystemMessageService systemMessageService;

    @Autowired
    private MailUtil mailUtil;

    @Autowired
    private WebSocketPushService webSocketPushService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private ArticleAuditService self;

    @Autowired
    private AiHubService aiHubService;

    @Autowired
    private ArticleTagService articleTagService;

    @Autowired
    private ArticleSearchIndexService articleSearchIndexService;

    @Autowired
    private IpRegionService ipRegionService;

    @Autowired
    private ArticleMediaService articleMediaService;

    @Autowired
    private ArticleHotRankingService articleHotRankingService;

    @Autowired
    private ArticlePublishSideEffectService articlePublishSideEffectService;

    private ArticleAuditSubmitGuardChain articleAuditSubmitGuardChain = ArticleAuditSubmitGuardChain.defaultChain();

    @Autowired(required = false)
    public void setArticleAuditSubmitGuardChain(ArticleAuditSubmitGuardChain articleAuditSubmitGuardChain) {
        if (articleAuditSubmitGuardChain != null) {
            this.articleAuditSubmitGuardChain = articleAuditSubmitGuardChain;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submitForAudit(Long articleId, Long loginUserId, Boolean notifyEmail) {
        if (articleId == null || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        User author = userService.queryUserByUserId(loginUserId);
        Article article = articleMapper.selectByIdForUpdate(articleId);
        checkArticleAuditSubmitGuard(new ArticleAuditSubmitContext(articleId, loginUserId, author, article));
        int curRetry = article.getAuditRetryCount() == null ? 0 : article.getAuditRetryCount();
        String taskId = UUID.randomUUID().toString();
        Byte oldStatus = article.getStatus();
        boolean wasPublished = ArticleStatus.isPublished(oldStatus);
        String ipRegion = ipRegionService.resolveRegion(RequestIpUtils.resolveClientIp());
        LambdaUpdateWrapper<Article> auditUpdate = new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, oldStatus)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .set(Article::getStatus, ArticleStatus.PENDING_AUDIT.getCode())
                .set(Article::getAuditTaskId, taskId)
                .set(Article::getAuditNotifyEmail, Boolean.TRUE.equals(notifyEmail) ? AUDIT_NOTIFY_EMAIL_ON : AUDIT_NOTIFY_EMAIL_OFF)
                .set(Article::getAuditRetryCount, curRetry + 1)
                .set(Article::getAuditSubmittedAt, new Date())
                .set(Article::getAuditFinishedAt, null)
                .set(Article::getAuditResultMessage, null);
        if (StringUtils.hasText(ipRegion)) {
            auditUpdate.set(Article::getIpRegion, ipRegion);
        }
        int updated = articleMapper.update(null, auditUpdate);
        if (updated <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_STATUS_INVALID));
        }
        List<String> imageUrls = articleMediaService.queryArticleImageUrls(articleId);
        String videoUrl = null;
        if (article.getMediaType() != null && article.getMediaType() == 1
                && StringUtils.hasText(article.getVideoUrl())) {
            videoUrl = article.getVideoUrl().trim();
        }
        ArticleAuditTaskMqVO task = new ArticleAuditTaskMqVO(
                taskId,
                articleId,
                loginUserId,
                article.getTitle(),
                article.getContent(),
                StringUtils.hasText(article.getCoverImg()) ? article.getCoverImg() : null,
                imageUrls,
                videoUrl,
                System.currentTimeMillis()
        );
        forumProducer.sendArticleAuditTask(task);
        if (wasPublished) {
            final Long boardId = article.getBoardId();
            TransactionHooks.afterCommit(() ->
                    articlePublishSideEffectService.rollbackPublishedExposure(articleId, boardId, loginUserId));
        }
        log.info("提交审核成功: articleId={}, userId={}, taskId={}, retry={}/{}",
                articleId, loginUserId, taskId, curRetry + 1, Constant.ARTICLE_AUDIT_MAX_RETRY);
        return taskId;
    }

    private void checkArticleAuditSubmitGuard(ArticleAuditSubmitContext context) {
        ArticleAuditSubmitGuardResult result = articleAuditSubmitGuardChain.check(context);
        if (!result.isPassed()) {
            throw new ApplicationException(result.getErrorResult());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyAuditResult(ArticleAuditResultMqVO result) {
        if (result == null || result.getTaskId() == null || result.getArticleId() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String taskId = result.getTaskId();
        Long articleId = result.getArticleId();
        String dedupKey = Constant.REDIS_KEY_AUDIT_RESULT_DEDUP + taskId;
        try {
            String marker = stringRedisTemplate.opsForValue().get(dedupKey);
            if ("done".equals(marker)) {
                log.info("审核结果重复回调(命中 Redis 标记), 已忽略: taskId={}", taskId);
                return;
            }
        } catch (Exception ignored) {
            // Redis 不可用时仍走 DB CAS 兜底
        }
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE));
        if (article == null) {
            log.warn("审核结果对应帖子不存在: articleId={}, taskId={}", articleId, taskId);
            return;
        }
        if (article.getStatus() == null || article.getStatus() != ArticleStatus.PENDING_AUDIT.getCode()
                || !Objects.equals(article.getAuditTaskId(), taskId)) {
            log.info("审核结果对应任务已失效, 忽略: articleId={}, taskId={}, currentStatus={}",
                    articleId, taskId, article.getStatus());
            return;
        }
        String finalStatus = result.getFinalStatus() == null ? "AUDIT_ERROR" : result.getFinalStatus().toUpperCase();
        Date now = new Date();
        boolean applied;
        switch (finalStatus) {
            case "APPROVED":
                applied = applyAuditApproved(article, result, now);
                break;
            case "REJECTED":
                applied = applyAuditRejected(article, result, now);
                break;
            default:
                applied = applyAuditError(article, result, now);
                break;
        }
        if (applied) {
            try {
                stringRedisTemplate.opsForValue().set(dedupKey, "done",
                        Constant.REDIS_TTL_AUDIT_RESULT_DEDUP, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private boolean applyAuditApproved(Article article, ArticleAuditResultMqVO result, Date now) {
        Long articleId = article.getId();
        Long userId = article.getUserId();
        String fallbackRegion = article.getIpRegion();
        if (!StringUtils.hasText(fallbackRegion)) {
            User author = userService.getUserInfoById(userId);
            if (author != null) {
                fallbackRegion = author.getIpRegion();
            }
        }
        LambdaUpdateWrapper<Article> approvedUpdate = new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ArticleStatus.PENDING_AUDIT.getCode())
                .eq(Article::getAuditTaskId, result.getTaskId())
                .set(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .set(Article::getAuditFinishedAt, now)
                .set(Article::getAuditResultMessage,
                        StringUtils.hasText(result.getFinalReason()) ? result.getFinalReason() : "审核通过");
        if (StringUtils.hasText(fallbackRegion)) {
            approvedUpdate.set(Article::getIpRegion, fallbackRegion);
        }
        int updated = articleMapper.update(null, approvedUpdate);
        if (updated <= 0) {
            log.warn("APPROVED 扭转失败 (并发): articleId={}, taskId={}", articleId, result.getTaskId());
            return false;
        }
        articlePublishSideEffectService.promotePublishedExposure(articleId, userId, article.getBoardId());
        articleHotRankingService.addToHotRanking(articleId);
        if (StringUtils.hasText(result.getSummary())) {
            stringRedisTemplate.opsForValue().set(
                    Constant.REDIS_KEY_ARTICLE_SUMMARY + articleId,
                    result.getSummary(),
                    Constant.REDIS_TTL_ARTICLE_SUMMARY,
                    TimeUnit.SECONDS);
        }
        Article published = articleMapper.selectById(articleId);
        if (published != null) {
            User author = userService.getUserInfoById(userId);
            RagArticleIndexDTO ragPayload = new RagArticleIndexDTO();
            ragPayload.setArticleId(articleId);
            ragPayload.setTitle(published.getTitle());
            ragPayload.setContent(published.getContent());
            ragPayload.setMediaType(published.getMediaType() != null ? published.getMediaType().intValue() : 0);
            ragPayload.setVideoUrl(published.getVideoUrl());
            ragPayload.setCoverUrl(published.getCoverImg());
            ragPayload.setSummary(result.getSummary());
            ragPayload.setAuthorNickname(author != null ? author.getNickname() : "");
            ragPayload.setTagNames(articleTagService.tagNamesByArticleId(articleId));
            aiHubService.indexArticleRag(ragPayload);
            articleSearchIndexService.syncPublishedArticle(articleId);
        }
        notifyAuditResult(article, result,
                Constant.SYSTEM_MSG_TYPE_AUDIT_PASS,
                Constant.SYSTEM_MSG_TITLE_AUDIT_PASS,
                String.format("你的帖子《%s》已通过审核, 自动发布到论坛.", safeTitle(article.getTitle())));
        log.info("APPROVED 处理完成: articleId={}, taskId={}", articleId, result.getTaskId());
        return true;
    }

    private boolean applyAuditRejected(Article article, ArticleAuditResultMqVO result, Date now) {
        Long articleId = article.getId();
        String reason = StringUtils.hasText(result.getFinalReason()) ? result.getFinalReason() : "内容违规";
        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ArticleStatus.PENDING_AUDIT.getCode())
                .eq(Article::getAuditTaskId, result.getTaskId())
                .set(Article::getStatus, ArticleStatus.REJECTED.getCode())
                .set(Article::getAuditFinishedAt, now)
                .set(Article::getAuditResultMessage, truncate(reason, 500)));
        if (updated <= 0) {
            log.warn("REJECTED 扭转失败 (并发): articleId={}, taskId={}", articleId, result.getTaskId());
            return false;
        }
        notifyAuditResult(article, result,
                Constant.SYSTEM_MSG_TYPE_AUDIT_FAIL,
                Constant.SYSTEM_MSG_TITLE_AUDIT_FAIL,
                String.format("你的帖子《%s》未通过审核, 原因: %s. 请修改后重新提交.",
                        safeTitle(article.getTitle()), reason));
        log.info("REJECTED 处理完成: articleId={}, taskId={}, reason={}", articleId, result.getTaskId(), reason);
        return true;
    }

    private boolean applyAuditError(Article article, ArticleAuditResultMqVO result, Date now) {
        Long articleId = article.getId();
        String reason = StringUtils.hasText(result.getFinalReason()) ? result.getFinalReason() : "审核服务异常";
        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ArticleStatus.PENDING_AUDIT.getCode())
                .eq(Article::getAuditTaskId, result.getTaskId())
                .set(Article::getStatus, ArticleStatus.AUDIT_ERROR.getCode())
                .set(Article::getAuditFinishedAt, now)
                .set(Article::getAuditResultMessage, truncate(reason, 500)));
        if (updated <= 0) {
            log.warn("AUDIT_ERROR 扭转失败 (并发): articleId={}, taskId={}", articleId, result.getTaskId());
            return false;
        }
        notifyAuditResult(article, result,
                Constant.SYSTEM_MSG_TYPE_AUDIT_ERROR,
                Constant.SYSTEM_MSG_TITLE_AUDIT_ERROR,
                String.format("你的帖子《%s》审核异常: %s. 请稍后重新提交.",
                        safeTitle(article.getTitle()), reason));
        log.warn("AUDIT_ERROR 处理完成: articleId={}, taskId={}", articleId, result.getTaskId());
        return true;
    }

    private void notifyAuditResult(Article article, ArticleAuditResultMqVO result,
                                   Byte sysMsgType, String title, String content) {
        Long userId = article.getUserId();
        Long articleId = article.getId();
        Long systemMsgId = null;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("articleId", articleId);
            payload.put("taskId", result.getTaskId());
            payload.put("finalStatus", result.getFinalStatus());
            String payloadJson;
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (Exception e) {
                payloadJson = null;
            }
            systemMsgId = systemMessageService.createMessage(userId, sysMsgType, title, truncate(content, 500),
                    articleId, payloadJson);
        } catch (Exception e) {
            log.error("写入系统消息失败: userId={}, articleId={}", userId, articleId, e);
        }
        pushAuditRealtimeNotify(userId, articleId, result, title, content, systemMsgId);
        if (article.getAuditNotifyEmail() != null && article.getAuditNotifyEmail() == AUDIT_NOTIFY_EMAIL_ON) {
            sendAuditEmail(userId, title, content);
        }
    }

    private void pushAuditRealtimeNotify(Long userId, Long articleId, ArticleAuditResultMqVO result,
                                         String title, String content, Long systemMsgId) {
        if (userId == null || result == null) {
            return;
        }
        try {
            int statusAfter = resolveStatusAfterAudit(result.getFinalStatus());
            Map<String, Object> auditWs = new LinkedHashMap<>();
            auditWs.put("type", "audit_result");
            auditWs.put("articleId", articleId);
            auditWs.put("taskId", result.getTaskId());
            auditWs.put("finalStatus", result.getFinalStatus());
            auditWs.put("status", statusAfter);
            auditWs.put("resultMessage", truncate(content, 500));
            auditWs.put("title", title);
            webSocketPushService.push(userId, objectMapper.writeValueAsString(auditWs));
            Map<String, Object> sysWs = new LinkedHashMap<>();
            sysWs.put("type", "system_message");
            sysWs.put("messageId", systemMsgId);
            sysWs.put("articleId", articleId);
            sysWs.put("title", title);
            sysWs.put("content", truncate(content, 500));
            sysWs.put("finalStatus", result.getFinalStatus());
            webSocketPushService.push(userId, objectMapper.writeValueAsString(sysWs));
        } catch (Exception e) {
            log.warn("审核结果 WebSocket 推送失败 userId={}, articleId={}", userId, articleId, e);
        }
    }

    private int resolveStatusAfterAudit(String finalStatus) {
        if (finalStatus == null) {
            return ArticleStatus.AUDIT_ERROR.getCode();
        }
        return switch (finalStatus.toUpperCase()) {
            case "APPROVED" -> ArticleStatus.PUBLISHED.getCode();
            case "REJECTED" -> ArticleStatus.REJECTED.getCode();
            default -> ArticleStatus.AUDIT_ERROR.getCode();
        };
    }

    private void sendAuditEmail(Long userId, String title, String content) {
        try {
            User user = userService.queryUserByUserId(userId);
            if (user == null || !StringUtils.hasText(user.getEmail())) {
                log.info("跳过邮件通知: 用户未绑定邮箱 userId={}", userId);
                return;
            }
            String email = PiiUtils.decrypt(user.getEmail());
            if (!StringUtils.hasText(email)) {
                log.info("跳过邮件通知: 邮箱解密为空 userId={}", userId);
                return;
            }
            mailUtil.sendSampleMail(email, title, content);
            log.info("审核邮件通知已发送: userId={}", userId);
        } catch (Exception e) {
            log.error("发送审核邮件失败: userId={}", userId, e);
        }
    }

    private static String safeTitle(String title) {
        if (title == null) {
            return "(无标题)";
        }
        return title.length() > 30 ? title.substring(0, 30) + "..." : title;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    @Override
    public AuditStatusResponse getAuditStatus(Long articleId, Long loginUserId) {
        if (articleId == null || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Article article = requireArticle(articleId);
        if (!Objects.equals(article.getUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AUDIT_NOT_AUTHOR));
        }
        AuditStatusResponse resp = new AuditStatusResponse();
        resp.setArticleId(articleId);
        resp.setStatus(article.getStatus());
        resp.setStatusText(statusText(article.getStatus()));
        resp.setTaskId(article.getAuditTaskId());
        resp.setResultMessage(article.getAuditResultMessage());
        int retry = article.getAuditRetryCount() == null ? 0 : article.getAuditRetryCount();
        resp.setRetryCount(retry);
        resp.setRetryLimit(Constant.ARTICLE_AUDIT_MAX_RETRY);
        resp.setRetryLimitReached(retry >= Constant.ARTICLE_AUDIT_MAX_RETRY);
        resp.setSubmittedAt(article.getAuditSubmittedAt());
        resp.setFinishedAt(article.getAuditFinishedAt());
        return resp;
    }

    private Article requireArticle(Long articleId) {
        Article info = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getId, articleId)
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN));
        if (info == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return info;
    }

    private static String statusText(Byte status) {
        if (status == null) {
            return "未知";
        }
        if (status == ArticleStatus.DRAFT.getCode()) {
            return ArticleStatus.DRAFT.getMessage();
        }
        if (status == ArticleStatus.PENDING_AUDIT.getCode()) {
            return ArticleStatus.PENDING_AUDIT.getMessage();
        }
        if (status == ArticleStatus.APPROVED.getCode()) {
            return ArticleStatus.APPROVED.getMessage();
        }
        if (status == ArticleStatus.REJECTED.getCode()) {
            return ArticleStatus.REJECTED.getMessage();
        }
        if (status == ArticleStatus.AUDIT_ERROR.getCode()) {
            return ArticleStatus.AUDIT_ERROR.getMessage();
        }
        if (status == ArticleStatus.PUBLISHED.getCode()) {
            return ArticleStatus.PUBLISHED.getMessage();
        }
        return "未知";
    }

    @Override
    public int sweepStuckAuditTasks() {
        long timeoutMs = Constant.ARTICLE_AUDIT_TIMEOUT_SECONDS * 1000;
        Date cutoff = new Date(System.currentTimeMillis() - timeoutMs);
        List<Article> stuck = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, ArticleStatus.PENDING_AUDIT.getCode())
                .ne(Article::getDeleteState, DELETE_TRUE)
                .lt(Article::getAuditSubmittedAt, cutoff));
        if (stuck.isEmpty()) {
            return 0;
        }
        int handled = 0;
        for (Article a : stuck) {
            try {
                ArticleAuditResultMqVO mock = new ArticleAuditResultMqVO();
                mock.setTaskId(a.getAuditTaskId());
                mock.setArticleId(a.getId());
                mock.setUserId(a.getUserId());
                mock.setTitle(a.getTitle());
                mock.setFinalStatus("AUDIT_ERROR");
                mock.setFinalReason("审核服务长时间未响应, 已自动归类为异常, 请重新提交");
                mock.setFinishedAt(System.currentTimeMillis());
                self.applyAuditResult(mock);
                handled++;
            } catch (Exception e) {
                log.error("审核任务兜底处理失败: articleId={}", a.getId(), e);
            }
        }
        if (handled > 0) {
            log.warn("兜底任务处理完毕: {} 条 PENDING 审核被强制转为 AUDIT_ERROR", handled);
        }
        return handled;
    }
}
