package org.example.forumdemo.service.impl.mascot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.common.utils.AiAuditUtils;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.entity.vo.ai.RagArticleVectorHitVO;
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
    private static final double MIN_SCORE = 0.42;
    /** 最高分不足则整批不展示（避免泛语义误召回） */
    private static final double TOP_MIN_TO_SHOW = 0.46;
    /** 相对 top 分比例，低于此视为弱相关不展示 */
    private static final double RELATIVE_TO_TOP = 0.93;
    /** 相邻结果分差超过此值则截断（只保留第一梯队） */
    private static final double SCORE_GAP_MAX = 0.055;
    /** 高于此分可免标题关键词校验 */
    private static final double HIGH_SCORE_BYPASS = 0.52;

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
            List<RagArticleVectorHitVO> vectorRanked = aiHubService.ragArticleVectorRanked(query, List.of());
            if (!vectorRanked.isEmpty()) {
                List<Map<String, Object>> rankedMaps = toRankedMaps(vectorRanked);
                List<Map<String, Object>> fromVector = enrichTitles(query, rankedMaps, exclude);
                if (!fromVector.isEmpty()) {
                    return fromVector;
                }
            }

            // 2) 降级：MySQL 候选 + hybrid_rank / 向量融合
            List<Article> candidates = articleMapper.selectPage(
                    new Page<>(1, Constant.SEARCH_RAG_CANDIDATE_LIMIT, false),
                    buildCandidateQuery(query)).getRecords();
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
            return enrichFromRanked(query, ranked, byId, exclude);
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

    private List<Map<String, Object>> enrichTitles(
            String query, List<Map<String, Object>> ranked, Set<Long> exclude) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String, Object> row : ranked) {
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
            candidates.add(item);
        }
        return applyRelevanceCutoff(query, candidates);
    }

    private List<Map<String, Object>> enrichFromRanked(
            String query, List<Map<String, Object>> ranked, Map<Long, Article> byId, Set<Long> exclude) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String, Object> row : ranked) {
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
            candidates.add(item);
        }
        return applyRelevanceCutoff(query, candidates);
    }

    /** 绝对阈值 + 相对 top + 分差截断 + 标题词命中，返回 0~5 条 */
    private List<Map<String, Object>> applyRelevanceCutoff(String query, List<Map<String, Object>> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        candidates.sort(Comparator.comparingDouble(
                (Map<String, Object> m) -> ((Number) m.getOrDefault("score", 0)).doubleValue()).reversed());
        double top = ((Number) candidates.get(0).getOrDefault("score", 0)).doubleValue();
        if (top < TOP_MIN_TO_SHOW) {
            return Collections.emptyList();
        }
        double relativeFloor = Math.max(MIN_SCORE, top * RELATIVE_TO_TOP);
        List<Map<String, Object>> out = new ArrayList<>();
        double prevScore = -1;
        for (Map<String, Object> row : candidates) {
            if (out.size() >= MAX_RELATED) {
                break;
            }
            double score = ((Number) row.getOrDefault("score", 0)).doubleValue();
            if (score < relativeFloor) {
                break;
            }
            String title = String.valueOf(row.getOrDefault("title", ""));
            if (!passesSemanticGate(query, title, score)) {
                continue;
            }
            if (prevScore >= 0 && prevScore - score > SCORE_GAP_MAX) {
                break;
            }
            prevScore = score;
            out.add(row);
        }
        return out;
    }

    /** 低分时要求查询词与标题有字面重叠，过滤泛向量误匹配 */
    private boolean passesSemanticGate(String query, String title, double score) {
        if (score >= HIGH_SCORE_BYPASS) {
            return true;
        }
        if (!StringUtils.hasText(title)) {
            return false;
        }
        List<String> tokens = tokenizeKeyword(query);
        if (tokens.isEmpty()) {
            return score >= TOP_MIN_TO_SHOW;
        }
        String titleLower = title.toLowerCase();
        for (String token : tokens) {
            if (titleLower.contains(token.toLowerCase())) {
                return true;
            }
        }
        return false;
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
        List<Map<String, Object>> ranked = toRankedMaps(aiHubService.ragArticleVectorRanked(query, payload));
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String, Object> row : ranked) {
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
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("articleId", id);
            m.put("score", score);
            candidates.add(m);
        }
        return candidates;
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Article> buildCandidateQuery(String kw) {
        List<String> tokens = tokenizeKeyword(kw);
        return new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, DELETE_TRUE)
                .ne(Article::getState, STATE_FORBIDDEN)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .and(w -> {
                    w.like(Article::getTitle, kw).or().like(Article::getContent, kw);
                    for (String t : tokens) {
                        w.or().like(Article::getTitle, t).or().like(Article::getContent, t);
                    }
                })
                .orderByDesc(Article::getUpdateTime);
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

    private static List<Map<String, Object>> toRankedMaps(List<RagArticleVectorHitVO> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> ranked = new ArrayList<>(hits.size());
        for (RagArticleVectorHitVO hit : hits) {
            if (hit.getArticleId() == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("articleId", hit.getArticleId());
            row.put("score", hit.getScore() != null ? hit.getScore() : 0.0);
            ranked.add(row);
        }
        return ranked;
    }
}
