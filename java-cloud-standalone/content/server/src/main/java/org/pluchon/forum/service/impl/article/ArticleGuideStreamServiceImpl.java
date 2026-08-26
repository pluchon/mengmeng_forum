package org.pluchon.forum.service.impl.article;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.entity.dto.RagArticleIndexDTO;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.service.remote.ContentAiGatewayService;
import org.pluchon.forum.service.interfaces.article.ArticleGuideStreamService;
import org.pluchon.forum.service.interfaces.article.ArticleService;
import org.pluchon.forum.service.impl.remote.ContentUserLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// 帖子 AI 智能导读流式输出 独立 Bean，避免拖垮 ArticleServiceImpl 注册
@Slf4j
@Service
public class ArticleGuideStreamServiceImpl implements ArticleGuideStreamService {

    @Autowired
    @Lazy
    private ArticleService articleService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ContentAiGatewayService aiHubService;

    @Autowired
    private ContentUserLookupService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void streamArticleGuide(Long articleId, SseEmitter emitter) {
        String guideKey = Constant.REDIS_KEY_ARTICLE_GUIDE + articleId;
        Article article = articleService.selectArticleByArticleId(articleId);
        if (article == null) {
            sendGuideSseChunk(emitter, Constant.SUMMARY_ARTICLE_NOT_FOUND);
            emitter.complete();
            return;
        }
        String content = article.getContent();
        String plainText = (content == null) ? "" : content.replaceAll("<[^>]+>", "").trim();
        if (plainText.length() < 50) {
            sendGuideSseChunk(emitter, String.format(Constant.SUMMARY_ARTICLE_TOO_SHORT, plainText.length()));
            emitter.complete();
            return;
        }
        String cached = stringRedisTemplate.opsForValue().get(guideKey);
        if (cached != null && !cached.isBlank()) {
            try {
                streamGuideTextToClient(emitter, cached);
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return;
        }
        try {
            String summary = aiHubService.summarize(content);
            if (summary == null || summary.isEmpty()) {
                sendGuideSseChunk(emitter, "AI 未能生成有效摘要，请稍后重试或充实正文后再试。");
            } else if (!isSummaryTooSimilarToBody(summary, plainText)) {
                streamGuideTextToClient(emitter, summary);
                Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                        guideKey, summary, Constant.REDIS_TTL_ARTICLE_GUIDE, TimeUnit.SECONDS);
                if (Boolean.TRUE.equals(first)) {
                    indexGuideSummaryToRag(article, summary);
                }
            } else {
                sendGuideSseChunk(emitter, "AI 返回内容与正文过于相似，请充实正文后再尝试智能导读。");
            }
            emitter.complete();
        } catch (Exception e) {
            log.warn("帖子 {} 智能导读流式失败: {}", articleId, e.getMessage());
            try {
                sendGuideSseChunk(emitter, Constant.SUMMARY_AI_SERVICE_UNAVAILABLE);
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    private void sendGuideSseChunk(SseEmitter emitter, String text) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("text", text);
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            if (e instanceof IOException io) {
                throw new UncheckedIOException(io);
            }
            throw new IllegalStateException(e);
        }
    }

    private void streamGuideTextToClient(SseEmitter emitter, String text) {
        for (int i = 0; i < text.length(); i++) {
            sendGuideSseChunk(emitter, String.valueOf(text.charAt(i)));
        }
    }

    private void indexGuideSummaryToRag(Article published, String guideSummary) {
        if (published == null || guideSummary == null || guideSummary.isBlank()) {
            return;
        }
        try {
            UserInternalVO author = userService.getUserInfoById(published.getUserId());
            RagArticleIndexDTO ragPayload = new RagArticleIndexDTO();
            ragPayload.setArticleId(published.getId());
            ragPayload.setTitle(published.getTitle());
            ragPayload.setContent(published.getContent());
            ragPayload.setMediaType(published.getMediaType() != null ? published.getMediaType().intValue() : 0);
            ragPayload.setVideoUrl(published.getVideoUrl());
            ragPayload.setCoverUrl(published.getCoverImg());
            ragPayload.setSummary(guideSummary);
            if (author != null) {
                ragPayload.setAuthorNickname(author.getNickname());
            }
            aiHubService.indexArticleRag(ragPayload);
        } catch (Exception e) {
            log.warn("帖子 {} 导读向量入库失败: {}", published.getId(), e.getMessage());
        }
    }

    private static boolean isSummaryTooSimilarToBody(String summary, String plainText) {
        if (summary == null || plainText == null) {
            return false;
        }
        String sNorm = summary.replaceAll("\\s+", "");
        String pNorm = plainText.replaceAll("\\s+", "");
        if (sNorm.isEmpty() || pNorm.isEmpty()) {
            return false;
        }
        if (sNorm.equals(pNorm)) {
            return true;
        }
        int minLen = Math.min(sNorm.length(), pNorm.length());
        if (minLen < 20) {
            return false;
        }
        return pNorm.length() > 40 && (sNorm.contains(pNorm) || pNorm.contains(sNorm));
    }
}
