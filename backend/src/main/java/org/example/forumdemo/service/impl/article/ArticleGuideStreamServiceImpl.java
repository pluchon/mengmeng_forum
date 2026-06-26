package org.example.forumdemo.service.impl.article;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.config.AiHubUrls;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.entity.dto.ai.RagArticleIndexDTO;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.service.interfaces.ai.AiHubService;
import org.example.forumdemo.service.interfaces.article.ArticleGuideStreamService;
import org.example.forumdemo.service.interfaces.article.ArticleService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// 帖子 AI 智能导读流式输出（独立 Bean，避免拖垮 ArticleServiceImpl 注册）
@Slf4j
@Service
public class ArticleGuideStreamServiceImpl implements ArticleGuideStreamService {

    @Autowired
    @Lazy
    private ArticleService articleService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AiHubService aiHubService;

    @Autowired
    private UserService userService;

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
        StringBuilder full = new StringBuilder();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(AiHubUrls.summarizeStreamUrl()).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(180_000);
            byte[] body = objectMapper.writeValueAsBytes(Collections.singletonMap("content", content));
            conn.getOutputStream().write(body);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                sendGuideSseChunk(emitter, Constant.SUMMARY_AI_SERVICE_UNAVAILABLE);
                emitter.complete();
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> chunk = objectMapper.readValue(payload, Map.class);
                    Object textObj = chunk.get("text");
                    if (textObj == null) {
                        continue;
                    }
                    String piece = String.valueOf(textObj);
                    if (piece.isEmpty()) {
                        continue;
                    }
                    full.append(piece);
                    emitter.send(SseEmitter.event().data(payload));
                }
            }
            String summary = full.toString().trim();
            if (summary.isEmpty()) {
                sendGuideSseChunk(emitter, "AI 未能生成有效摘要，请稍后重试或充实正文后再试。");
            } else if (!isSummaryTooSimilarToBody(summary, plainText)) {
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
        } finally {
            if (conn != null) {
                conn.disconnect();
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
            User author = userService.getUserInfoById(published.getUserId());
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
