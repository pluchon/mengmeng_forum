package org.example.forumdemo.service.impl.mascot;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.common.utils.AiAuditUtils;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.service.interfaces.ai.AiHubService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 看板娘对话：站内帖子 RAG（优先 qwen3-vl-embedding 向量库，最多 5 条，按相关度降序）.
 */
@Slf4j
@Component
public class MascotArticleRagHelper {

    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_FORBIDDEN = 1;
    private static final int RAG_TEXT_TRUNCATE = 1200;
    private static final int RAG_EXCERPT_HEAD = 700;
    private static final int RAG_EXCERPT_TAIL = 400;
    private static final int MAX_RELATED = 5;
    /** 向量相关度阈值（与 ai-server rag.mascot_min_score 对齐） */
    private static final double MIN_SCORE = 0.10;

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private AiHubService aiHubService;

    public List<Map<String, Object>> recommendRelatedArticles(String userMessage) {
        return recommendRelatedArticles(userMessage, List.of());
    }

    public List<Map<String, Object>> recommendRelatedArticles(String userMessage, List<Long> excludeArticleIds) {
        if (!StringUtils.hasText(userMessage) || userMessage.trim().length() < 2) {
            return Collections.emptyList();
        }
        String query = userMessage.trim();
        Set<Long> exclude = toExcludeSet(excludeArticleIds);
        try {
            // 1) 优先全库向量召回（Redis + qwen3-vl-embedding）
            List<Map<String, Object>> vectorRanked = aiHubService.ragArticleVectorRanked(query, List.of());
            if (!vectorRanked.isEmpty()) {
                List<Map<String, Object>> fromVector = enrichTitles(vectorRanked, exclude);
                if (!fromVector.isEmpty()) {
                    return fromVector;
                }
            }

            // 2) 降级：MySQL 候选 + hybrid_rank / 向量融合
            List<Article> candidates = articleMapper.selectList(buildCandidateQuery(query));
            if (candidates.isEmpty()) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> payload = new ArrayList<>(candidates.size());
            Map<Long, Article> byId = new HashMap<>(candidates.size() * 2);
            for (Article a : candidates) {
                byId.put(a.getId(), a);
                Map<String, Object> item = new HashMap<>(2);
                item.put("articleId", a.getId());
                item.put("text", buildRagText(a));
                payload.add(item);
            }
            List<Map<String, Object>> ranked = AiAuditUtils.ragSearchArticlesRanked(query, payload);
            if (ranked.isEmpty()) {
                ranked = fallbackVectorRank(query, payload, exclude);
            }
            return enrichFromRanked(ranked, byId, exclude);
        } catch (Exception e) {
            log.warn("看板娘帖子 RAG 推荐失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static Set<Long> toExcludeSet(List<Long> excludeArticleIds) {
        if (excludeArticleIds == null || excludeArticleIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> out = new HashSet<>();
        for (Long id : excludeArticleIds) {
            if (id != null && id > 0) {
                out.add(id);
            }
        }
        return out;
    }

    private List<Map<String, Object>> enrichTitles(List<Map<String, Object>> ranked, Set<Long> exclude) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : ranked) {
            if (out.size() >= MAX_RELATED) {
                break;
            }
            double score = row.get("score") instanceof Number n ? n.doubleValue() : 0.0;
            if (score < MIN_SCORE) {
                continue;
            }
            long id;
            try {
                id = Long.parseLong(String.valueOf(row.get("articleId")));
            } catch (NumberFormatException e) {
                continue;
            }
            if (exclude.contains(id)) {
                continue;
            }
            Article a = articleMapper.selectById(id);
            if (a == null || !isPublishedVisible(a) || !StringUtils.hasText(a.getTitle())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("articleId", id);
            item.put("title", a.getTitle().trim());
            item.put("score", score);
            out.add(item);
        }
        out.sort(Comparator.comparingDouble(
                (Map<String, Object> m) -> ((Number) m.getOrDefault("score", 0)).doubleValue()).reversed());
        return out;
    }

    private List<Map<String, Object>> enrichFromRanked(
            List<Map<String, Object>> ranked, Map<Long, Article> byId, Set<Long> exclude) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : ranked) {
            if (out.size() >= MAX_RELATED) {
                break;
            }
            Object idObj = row.get("articleId");
            if (idObj == null) {
                continue;
            }
            double score = row.get("score") instanceof Number n ? n.doubleValue() : 0.0;
            if (score < MIN_SCORE) {
                continue;
            }
            long id;
            try {
                id = Long.parseLong(String.valueOf(idObj));
            } catch (NumberFormatException e) {
                continue;
            }
            if (exclude.contains(id)) {
                continue;
            }
            Article a = byId.get(id);
            if (a == null || !StringUtils.hasText(a.getTitle())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("articleId", id);
            item.put("title", a.getTitle().trim());
            item.put("score", score);
            out.add(item);
        }
        out.sort(Comparator.comparingDouble(
                (Map<String, Object> m) -> ((Number) m.getOrDefault("score", 0)).doubleValue()).reversed());
        return out;
    }

    private boolean isPublishedVisible(Article a) {
        Byte del = a.getDeleteState();
        Byte st = a.getState();
        return (del == null || del.byteValue() != DELETE_TRUE)
                && (st == null || st.byteValue() != STATE_FORBIDDEN)
                && ArticleStatus.isPublished(a.getStatus());
    }

    private List<Map<String, Object>> fallbackVectorRank(
            String query, List<Map<String, Object>> payload, Set<Long> exclude) {
        List<Long> ids = aiHubService.ragVectorSearchArticles(query, payload);
        List<Map<String, Object>> out = new ArrayList<>();
        int rank = ids.size();
        for (Long id : ids) {
            if (out.size() >= MAX_RELATED) {
                break;
            }
            if (id == null || exclude.contains(id)) {
                rank--;
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("articleId", id);
            m.put("score", Math.max(MIN_SCORE, 0.5 + (rank-- * 0.02)));
            out.add(m);
        }
        return out;
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Article> buildCandidateQuery(String kw) {
        List<String> tokens = tokenizeKeyword(kw);
        return new QueryWrapper<Article>().lambda()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .and(w -> {
                    w.like(Article::getTitle, kw).or().like(Article::getContent, kw);
                    for (String t : tokens) {
                        w.or().like(Article::getTitle, t).or().like(Article::getContent, t);
                    }
                })
                .orderByDesc(Article::getUpdateTime)
                .last("LIMIT " + Constant.SEARCH_RAG_CANDIDATE_LIMIT);
    }

    private List<String> tokenizeKeyword(String kw) {
        if (!StringUtils.hasText(kw)) {
            return Collections.emptyList();
        }
        String[] parts = kw.trim().split("[\\s,，、；;|/\\\\]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (t.length() < 2 || out.contains(t)) {
                continue;
            }
            out.add(t);
            if (out.size() >= 8) {
                break;
            }
        }
        return out;
    }

    private static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static String buildExcerpt(String plain, int maxLen) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        if (plain.length() <= maxLen) {
            return plain;
        }
        if (maxLen <= RAG_EXCERPT_HEAD + RAG_EXCERPT_TAIL + 8) {
            return plain.substring(0, maxLen);
        }
        return plain.substring(0, RAG_EXCERPT_HEAD) + "\n…\n" + plain.substring(plain.length() - RAG_EXCERPT_TAIL);
    }

    private String buildRagText(Article a) {
        String title = a.getTitle() == null ? "" : a.getTitle();
        String body = buildExcerpt(stripHtml(a.getContent()), RAG_TEXT_TRUNCATE);
        StringBuilder sb = new StringBuilder();
        if (!title.isBlank()) {
            sb.append("标题: ").append(title).append('\n');
        }
        if (!body.isBlank()) {
            sb.append("正文:\n").append(body).append('\n');
        }
        return sb.toString().trim();
    }
}
