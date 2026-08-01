package org.pluchon.forum.service.impl.mascot;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.api.content.ArticleInternalVO;
import org.pluchon.forum.cloud.feign.ArticleInternalFeignClient;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.entity.vo.ai.RagArticleVectorHitVO;
import org.pluchon.forum.entity.vo.mascot.MascotRelatedArticleCandidate;
import org.pluchon.forum.service.interfaces.ai.AiHubService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final int MAX_CONFIRMED_CANDIDATES = 40;
    /** 向量相关度阈值（与 ai-server rag.mascot_min_score 对齐） */
    private static final double MIN_SCORE = 0.42;
    /** 最高分不足则整批不展示（避免泛语义误召回） */
    private static final double TOP_MIN_TO_SHOW = 0.46;
    /** 高于此分可免标题关键词校验 */
    private static final double HIGH_SCORE_BYPASS = 0.52;

    @Lazy
    @Resource
    private ArticleInternalFeignClient articleInternalFeignClient;

    @Resource
    private AiHubService aiHubService;

    /**
     * 用户明确同意后才执行的候选召回：保留足够多的高相似候选，交由业务层按热度和发布时间选出最终五条。
     */
    public List<MascotRelatedArticleCandidate> findConfirmedRelatedCandidates(String userMessage) {
        if (!StringUtils.hasText(userMessage) || userMessage.trim().length() < 2) {
            return Collections.emptyList();
        }
        String query = userMessage.trim();
        try {
            List<Map<String, Object>> vectorRanked = toRankedMaps(
                    aiHubService.ragArticleVectorRanked(query, List.of()));
            if (!vectorRanked.isEmpty()) {
                List<MascotRelatedArticleCandidate> vectorCandidates = enrichConfirmedCandidates(
                        query, vectorRanked, loadVisibleArticles(vectorRanked));
                if (!vectorCandidates.isEmpty()) {
                    return vectorCandidates;
                }
            }

            List<ArticleInternalVO> candidateVos = articleInternalFeignClient.searchCandidates(
                    query, Constant.SEARCH_RAG_CANDIDATE_LIMIT);
            List<ArticleInternalVO> candidates = new ArrayList<>();
            if (candidateVos != null) {
                for (ArticleInternalVO vo : candidateVos) {
                    if (vo != null) {
                        candidates.add(vo);
                    }
                }
            }
            if (candidates.isEmpty()) {
                return Collections.emptyList();
            }
            Map<Long, ArticleInternalVO> byId = new HashMap<>(candidates.size() * 2);
            List<Map<String, Object>> payload = new ArrayList<>(candidates.size());
            for (ArticleInternalVO article : candidates) {
                byId.put(article.getId(), article);
                Map<String, Object> item = new HashMap<>(2);
                item.put("articleId", article.getId());
                item.put("text", buildRagText(article));
                payload.add(item);
            }
            List<Map<String, Object>> ranked = toRankedMaps(aiHubService.ragArticleVectorRanked(query, payload));
            return enrichConfirmedCandidates(query, ranked, byId);
        } catch (Exception e) {
            log.warn("看板娘确认后的帖子 RAG 检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<Long, ArticleInternalVO> loadVisibleArticles(List<Map<String, Object>> ranked) {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> row : ranked) {
            try {
                long id = Long.parseLong(String.valueOf(row.get("articleId")));
                if (id > 0 && !ids.contains(id)) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // 忽略不合法向量库记录
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ArticleInternalVO> byId = new HashMap<>(ids.size() * 2);
        List<ArticleInternalVO> vos = articleInternalFeignClient.listByIds(ids);
        if (vos == null || vos.isEmpty()) {
            return Collections.emptyMap();
        }
        for (ArticleInternalVO vo : vos) {
            if (vo == null || !isPublishedVisible(vo)) {
                continue;
            }
            byId.put(vo.getId(), vo);
        }
        return byId;
    }

    private List<MascotRelatedArticleCandidate> enrichConfirmedCandidates(
            String query, List<Map<String, Object>> ranked, Map<Long, ArticleInternalVO> byId) {
        if (ranked == null || ranked.isEmpty() || byId.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, MascotRelatedArticleCandidate> unique = new LinkedHashMap<>();
        for (Map<String, Object> row : ranked) {
            double score = row.get("score") instanceof Number n ? n.doubleValue() : 0D;
            if (score < MIN_SCORE) {
                continue;
            }
            long id;
            try {
                id = Long.parseLong(String.valueOf(row.get("articleId")));
            } catch (NumberFormatException ignored) {
                continue;
            }
            ArticleInternalVO article = byId.get(id);
            if (article == null || !isPublishedVisible(article) || !StringUtils.hasText(article.getTitle())) {
                continue;
            }
            MascotRelatedArticleCandidate current = unique.get(id);
            if (current == null || score > current.getRelevanceScore()) {
                unique.put(id, new MascotRelatedArticleCandidate(article, score));
            }
        }
        List<MascotRelatedArticleCandidate> candidates = new ArrayList<>(unique.values());
        candidates.sort(Comparator.comparingDouble(MascotRelatedArticleCandidate::getRelevanceScore).reversed());
        if (candidates.isEmpty() || candidates.get(0).getRelevanceScore() < TOP_MIN_TO_SHOW) {
            return Collections.emptyList();
        }
        double floor = Math.max(MIN_SCORE, candidates.get(0).getRelevanceScore() * 0.78D);
        List<MascotRelatedArticleCandidate> result = new ArrayList<>();
        for (MascotRelatedArticleCandidate candidate : candidates) {
            if (candidate.getRelevanceScore() < floor) {
                break;
            }
            if (!passesSemanticGate(query, candidate.getArticle().getTitle(), candidate.getRelevanceScore())) {
                continue;
            }
            result.add(candidate);
            if (result.size() >= MAX_CONFIRMED_CANDIDATES) {
                break;
            }
        }
        return result;
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

    private boolean isPublishedVisible(ArticleInternalVO article) {
        Byte del = article.getDeleteState();
        Byte st = article.getState();
        return (del == null || del.byteValue() != DELETE_TRUE)
                && (st == null || st.byteValue() != STATE_FORBIDDEN)
                && ArticleStatus.isPublished(article.getStatus());
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

    private String buildRagText(ArticleInternalVO article) {
        String title = article.getTitle() == null ? "" : article.getTitle();
        String body = buildExcerpt(stripHtml(article.getContent()), RAG_TEXT_TRUNCATE);
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
