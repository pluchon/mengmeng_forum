package org.pluchon.forum.service.impl.ai;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.client.ai.AiPythonGatewayClient;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.converter.AiHubConverter;
import org.pluchon.forum.entity.dto.AiArticleCoverRequest;
import org.pluchon.forum.entity.dto.AiArticleTagRecommendRequest;
import org.pluchon.forum.entity.dto.AiMusicRecommendRequest;
import org.pluchon.forum.entity.dto.AiMusicSearchRequest;
import org.pluchon.forum.entity.dto.AiMusicTasteRecommendRequest;
import org.pluchon.forum.entity.dto.AiArticleTagSimilarityRequest;
import org.pluchon.forum.api.ai.AiGobangMoveRequest;
import org.pluchon.forum.api.ai.AiGobangMoveVO;
import org.pluchon.forum.entity.dto.AiCoverHintsRequest;
import org.pluchon.forum.entity.dto.AiCreatorInsightRequest;
import org.pluchon.forum.entity.dto.AiImageModerationBatchUrlRequest;
import org.pluchon.forum.entity.dto.AiImageRequest;
import org.pluchon.forum.entity.dto.AiPolishRequest;
import org.pluchon.forum.entity.dto.AiRecommendationArticleFeatureRequest;
import org.pluchon.forum.entity.dto.AiRecommendationProfileRequest;
import org.pluchon.forum.entity.dto.RagArticleIndexDTO;
import org.pluchon.forum.entity.dto.RagEmojiIndexDTO;
import org.pluchon.forum.entity.dto.RagMusicIndexDTO;
import org.pluchon.forum.entity.dto.RagUserIndexDTO;
import org.pluchon.forum.entity.vo.ai.AiHubCoverHintsResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubCreatorInsightResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubArticleCoverResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubArticleTagRecommendResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubArticleTagSimilarityResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubImageResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubMusicMatchResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubPolishResultVO;
import org.pluchon.forum.entity.vo.ai.AiImageModerationItemResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationFeatureResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationProfileResultVO;
import org.pluchon.forum.entity.vo.ai.RagArticleVectorHitVO;
import org.pluchon.forum.entity.vo.ai.RagUserVectorHitVO;
import org.pluchon.forum.service.interfaces.ai.AiHubService;
import org.pluchon.forum.service.interfaces.ai.AiQuotaService;
import org.pluchon.forum.service.security.AiUserLookupService;
import org.pluchon.forum.service.security.AiUserContext;
import org.pluchon.forum.entity.dto.AiModelUsageDTO;
import org.pluchon.forum.common.constant.Constant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiHubServiceImpl implements AiHubService {

    @Autowired
    private AiUserLookupService aiUserLookupService;

    @Autowired
    private AiQuotaService aiQuotaService;

    @Autowired
    private AiPointsBillingService aiPointsBillingService;

    // 站点搜索：帖子向量最低相关度 与 ai server rag.vector_min_score 对齐
    private static final double ARTICLE_SEARCH_MIN_SCORE = 0.20;
    // 站点搜索：用户向量最低相关度
    private static final double USER_SEARCH_MIN_SCORE = 0.28;

    @Autowired
    private AiPythonGatewayClient aiPythonGatewayClient;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> parseGatewayResponse(Map resp) {
        int code = parseHubCode(resp.get("code"));
        if (code != 200) {
            log.warn("AI Hub 业务码异常 path={} code={}", "/api/v1/gateway/invoke", code);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_ENGINE));
        }
        Object data = resp.get("data");
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("payload", data);
        return wrap;
    }

    private static int parseHubCode(Object codeObj) {
        if (codeObj instanceof Number n) {
            return n.intValue();
        }
        if (codeObj != null) {
            try {
                return Integer.parseInt(String.valueOf(codeObj).trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private Map<String, Object> invokeGateway(String taskType, String intent, Long userId, Map<String, Object> payload) {
        Map<String, Object> body = new HashMap<>();
        body.put("taskType", taskType);
        body.put("intent", intent);
        body.put("version", "v1");
        body.put("userContext", Collections.singletonMap("userId", userId));
        body.put("payload", payload);
        Map<String, Object> gateway = parseGatewayResponse(aiPythonGatewayClient.invoke(taskType, intent, body));
        Object success = gateway.get("success");
        if (!(success instanceof Boolean) || !((Boolean) success)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_ENGINE, "AI 模块执行失败"));
        }
        Map<String, Object> result = new HashMap<>();
        Object data = gateway.get("data");
        if (data instanceof Map<?, ?> map) {
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
        }
        result.put("usage", gateway.get("usage"));
        return result;
    }

    @Override
    public AiHubPolishResultVO polish(Long userId, AiPolishRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", request.getTitle());
        payload.put("content", request.getContent());
        payload.put("editorMode", request.getEditorMode());
        return AiHubConverter.toPolishResult(invokeGateway("POST_CREATION", "POLISH", userId, payload));
    }

    @Override
    public AiHubArticleCoverResultVO articleCover(Long userId, AiArticleCoverRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", request.getTitle());
        payload.put("content", request.getContent());
        payload.put("editorMode", request.getEditorMode());
        payload.put("userPrompt", request.getUserPrompt());
        payload.put("quality", request.getQuality());
        return AiHubConverter.toArticleCoverResult(
                invokeGateway("POST_CREATION", "COVER_GENERATE", userId, payload));
    }

    @Override
    public AiHubArticleTagRecommendResultVO recommendArticleTags(AiArticleTagRecommendRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", request.getTitle());
        payload.put("content", request.getContent());
        payload.put("editorMode", request.getEditorMode());
        payload.put("candidates", AiHubConverter.articleTagCandidatesToMaps(request.getCandidates()));
        AiUserContext user = aiUserLookupService.getById(request.getUserId());
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        aiQuotaService.consumeQwenFlash(user);
        try {
            AiHubArticleTagRecommendResultVO result = AiHubConverter.toArticleTagRecommendResult(
                    invokeGateway("POST_CREATION", "TAG_RECOMMEND", request.getUserId(), payload));
            aiPointsBillingService.billBatch(
                    user, "article_tag_recommend", result.getUsageItems(), "qwen3.7-flash",
                    request.getClientRequestId(), Constant.POINTS_SOURCE_AI_COMPANION, false);
            return result;
        } catch (RuntimeException exception) {
            aiQuotaService.releaseQwenFlash(user);
            throw exception;
        }
    }

    @Override
    public AiHubArticleTagSimilarityResultVO checkArticleTagSimilarity(
            AiArticleTagSimilarityRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("proposedName", request.getProposedName());
        payload.put("candidates", AiHubConverter.articleTagCandidatesToMaps(request.getCandidates()));
        return AiHubConverter.toArticleTagSimilarityResult(
                invokeGateway("POST_CREATION", "TAG_SIMILARITY", null, payload));
    }

    @Override
    public AiHubMusicMatchResultVO recommendMusic(AiMusicRecommendRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", request.getTitle());
        payload.put("content", request.getContent());
        payload.put("editorMode", request.getEditorMode());
        payload.put("candidates", AiHubConverter.musicCandidatesToMaps(request.getCandidates()));
        payload.put("mode", "recommend");
        AiUserContext user = aiUserLookupService.getById(request.getUserId());
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        aiQuotaService.consumeQwenFlash(user);
        try {
            AiHubMusicMatchResultVO result = AiHubConverter.toMusicMatchResult(
                    invokeGateway("POST_CREATION", "MUSIC_RECOMMEND", request.getUserId(), payload));
            aiPointsBillingService.billBatch(
                    user, "music_recommend", result.getUsageItems(), "qwen3.7-flash",
                    request.getClientRequestId(), Constant.POINTS_SOURCE_AI_COMPANION, false);
            return result;
        } catch (RuntimeException exception) {
            aiQuotaService.releaseQwenFlash(user);
            throw exception;
        }
    }

    @Override
    public AiHubMusicMatchResultVO searchMusic(AiMusicSearchRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", request.getQuery());
        payload.put("scope", request.getScope() == null ? "all" : request.getScope());
        payload.put("candidates", AiHubConverter.musicCandidatesToMaps(request.getCandidates()));
        AiUserContext user = aiUserLookupService.getById(request.getUserId());
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        aiQuotaService.consumeQwenFlash(user);
        try {
            AiHubMusicMatchResultVO result = AiHubConverter.toMusicMatchResult(
                    invokeGateway("POST_CREATION", "MUSIC_SEARCH", request.getUserId(), payload));
            aiPointsBillingService.billBatch(
                    user, "music_ai_search", result.getUsageItems(), "qwen3.7-flash",
                    request.getClientRequestId(), Constant.POINTS_SOURCE_AI_COMPANION, false);
            return result;
        } catch (RuntimeException exception) {
            aiQuotaService.releaseQwenFlash(user);
            throw exception;
        }
    }

    @Override
    public AiHubCoverHintsResultVO coverHints(Long userId, AiCoverHintsRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("articleText", request.getArticleText());
        return AiHubConverter.toCoverHintsResult(invokeGateway("POST_CREATION", "COVER_HINTS", userId, payload));
    }

    @Override
    public AiHubImageResultVO image(Long userId, AiImageRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("prompt", request.getPrompt());
        payload.put("quality", request.getQuality());
        return AiHubConverter.toImageResult(invokeGateway("IMAGE_GENERATION", "GENERATE", userId, payload));
    }

    @Override
    public AiRecommendationFeatureResultVO generateRecommendationArticleFeature(AiRecommendationArticleFeatureRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("articleId", request.getArticleId());
        payload.put("title", request.getTitle());
        payload.put("content", request.getContent());
        payload.put("boardName", request.getBoardName());
        return AiHubConverter.toRecommendationFeatureResult(
                invokeGateway("RECOMMENDATION", "ARTICLE_FEATURE", null, payload));
    }

    @Override
    public AiRecommendationProfileResultVO generateRecommendationProfile(Long userId, AiRecommendationProfileRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("explicitBoards", request.getExplicitBoards());
        payload.put("recent7", request.getRecent7());
        payload.put("recent14", request.getRecent14());
        payload.put("negativeRecent7", request.getNegativeRecent7());
        payload.put("negativeRecent14", request.getNegativeRecent14());
        return AiHubConverter.toRecommendationProfileResult(
                invokeGateway("RECOMMENDATION", "USER_PROFILE", userId, payload));
    }

    @Override
    public AiHubMusicMatchResultVO recommendMusicTaste(Long userId, AiMusicTasteRecommendRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("favorites", request.getFavorites() == null ? List.of() : request.getFavorites());
        payload.put("recentPlays", request.getRecentPlays() == null ? List.of() : request.getRecentPlays());
        payload.put("extras", request.getExtras() == null ? List.of() : request.getExtras());
        payload.put("candidates", request.getCandidates());
        return AiHubConverter.toMusicMatchResult(
                invokeGateway("RECOMMENDATION", "MUSIC_TASTE", userId, payload));
    }

    @Override
    public String summarize(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        Object summary = invokeGateway("POST_SUMMARY", "GENERATE", null, payload).get("summary");
        return summary != null ? String.valueOf(summary).trim() : null;
    }

    @Override
    public AiHubCreatorInsightResultVO generateCreatorInsight(AiCreatorInsightRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("periodLabel", request.getPeriodLabel());
        payload.put("startDate", request.getStartDate());
        payload.put("endDate", request.getEndDate());
        payload.put("readCount", request.getReadCount());
        payload.put("previousReadCount", request.getPreviousReadCount());
        payload.put("likeCount", request.getLikeCount());
        payload.put("previousLikeCount", request.getPreviousLikeCount());
        payload.put("workCount", request.getWorkCount());
        payload.put("previousWorkCount", request.getPreviousWorkCount());
        payload.put("newFollowerCount", request.getNewFollowerCount());
        payload.put("previousNewFollowerCount", request.getPreviousNewFollowerCount());
        payload.put("totalFollowerCount", request.getTotalFollowerCount());
        AiUserContext user = aiUserLookupService.getById(request.getUserId());
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        aiQuotaService.consumeQwenFlash(user);
        try {
            Map<String, Object> result = invokeGateway("CREATOR_INSIGHT", "GENERATE", request.getUserId(), payload);
            AiHubCreatorInsightResultVO response = AiHubConverter.toCreatorInsightResult(result);
            List<AiModelUsageDTO> usages = AiHubConverter.toUsageItems(result.get("usage"));
            aiPointsBillingService.billBatch(
                    user, "creator_insight", usages, "qwen3.7-flash",
                    request.getClientRequestId(), Constant.POINTS_SOURCE_AI_COMPANION, false);
            return response;
        } catch (RuntimeException exception) {
            aiQuotaService.releaseQwenFlash(user);
            throw exception;
        }
    }

    @Override
    public void indexArticleRag(RagArticleIndexDTO payload) {
        try {
            invokeGateway("RAG", "INDEX_ARTICLE", null, AiHubConverter.ragArticleIndexToMap(payload));
        } catch (Exception e) {
            log.warn("RAG 帖子索引失败 articleId={}: {}", payload != null ? payload.getArticleId() : null, e.getMessage());
        }
    }

    @Override
    public void indexUserRag(RagUserIndexDTO payload) {
        try {
            invokeGateway("RAG", "INDEX_USER", null, AiHubConverter.ragUserIndexToMap(payload));
        } catch (Exception e) {
            log.warn("RAG 用户索引失败 userId={}: {}", payload != null ? payload.getUserId() : null, e.getMessage());
        }
    }

    @Override
    public void removeArticleRag(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("articleId", articleId);
            invokeGateway("RAG", "REMOVE_ARTICLE", null, body);
        } catch (Exception e) {
            log.warn("RAG 帖子索引删除失败 articleId={}: {}", articleId, e.getMessage());
        }
    }

    @Override
    public List<Long> ragVectorSearchArticles(String query, List<Map<String, Object>> candidates) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", query.trim());
            body.put("candidates", candidates != null ? candidates : Collections.emptyList());
            Object results = searchResults("ARTICLE", body);
            if (!(results instanceof List<?> list)) {
                return Collections.emptyList();
            }
            List<Long> sorted = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Object scoreObj = m.get("score");
                    double score = scoreObj instanceof Number n ? n.doubleValue() : 0.0;
                    if (score < ARTICLE_SEARCH_MIN_SCORE) {
                        continue;
                    }
                }
                Long id = parseVectorHitId(item, "articleId", "article_id");
                if (id != null) {
                    sorted.add(id);
                }
            }
            if (sorted.isEmpty()) {
            }
            return sorted;
        } catch (Exception e) {
            log.warn("RAG 帖子向量检索失败 keyword={}: {}", query.trim(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private static Long parseVectorHitId(Object item, String... keys) {
        if (!(item instanceof Map<?, ?> m)) {
            return null;
        }
        for (String key : keys) {
            Object idObj = m.get(key);
            if (idObj == null) {
                continue;
            }
            if (idObj instanceof Number n) {
                return n.longValue();
            }
            try {
                return Long.valueOf(String.valueOf(idObj).trim());
            } catch (NumberFormatException ignore) {
                // 尝试下一个密钥
            }
        }
        return null;
    }

    @Override
    public List<Long> ragVectorSearchEmojis(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", query.trim());
            Object results = searchResults("EMOJI", body);
            if (!(results instanceof List<?> list)) {
                return Collections.emptyList();
            }
            List<Long> ranked = new ArrayList<>();
            for (Object item : list) {
                Long id = parseVectorHitId(item, "shopId", "shop_id");
                if (id != null && !ranked.contains(id)) {
                    ranked.add(id);
                }
            }
            return ranked;
        } catch (Exception e) {
            log.warn("RAG 表情包向量检索失败 keyword={}: {}", query.trim(), e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> ragVectorSearchMusic(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", query.trim());
            Object results = searchResults("MUSIC", body);
            if (!(results instanceof List<?> list)) {
                return Collections.emptyList();
            }
            List<String> ranked = new ArrayList<>();
            for (Object item : list) {
                String key = parseVectorHitMusicKey(item);
                if (key != null && !ranked.contains(key)) {
                    ranked.add(key);
                }
            }
            return ranked;
        } catch (Exception e) {
            log.warn("RAG 曲目向量检索失败 keyword={}: {}", query.trim(), e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<RagArticleVectorHitVO> ragArticleVectorRanked(String query, List<Map<String, Object>> candidates) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", query.trim());
            body.put("candidates", candidates != null ? candidates : Collections.emptyList());
            Object results = searchResults("ARTICLE", body);
            if (!(results instanceof List<?> list)) {
                return Collections.emptyList();
            }
            List<RagArticleVectorHitVO> ranked = AiHubConverter.toArticleVectorHits(list);
            ranked.sort((a, b) -> Double.compare(
                    b.getScore() != null ? b.getScore() : 0.0,
                    a.getScore() != null ? a.getScore() : 0.0));
            return ranked;
        } catch (Exception e) {
            log.warn("RAG 向量检索(含分数)失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<RagUserVectorHitVO> ragUserVectorRanked(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", query.trim());
            body.put("candidates", Collections.emptyList());
            Object results = searchResults("USER", body);
            if (!(results instanceof List<?> list)) {
                return Collections.emptyList();
            }
            List<RagUserVectorHitVO> ranked = AiHubConverter.toUserVectorHits(list);
            ranked.sort((a, b) -> Double.compare(
                    b.getScore() != null ? b.getScore() : 0.0,
                    a.getScore() != null ? a.getScore() : 0.0));
            return ranked;
        } catch (Exception e) {
            log.warn("RAG 用户向量检索(含分数)失败 keyword={}: {}", query.trim(), e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Long> ragVectorSearchUsers(String query, List<Map<String, Object>> candidates) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", query.trim());
            body.put("candidates", candidates != null ? candidates : Collections.emptyList());
            Object results = searchResults("USER", body);
            if (!(results instanceof List<?> list)) {
                return Collections.emptyList();
            }
            List<Long> sorted = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Object scoreObj = m.get("score");
                    double score = scoreObj instanceof Number n ? n.doubleValue() : 0.0;
                    if (score < USER_SEARCH_MIN_SCORE) {
                        continue;
                    }
                }
                Long id = parseVectorHitId(item, "userId", "user_id");
                if (id != null) {
                    sorted.add(id);
                }
            }
            return sorted;
        } catch (Exception e) {
            log.warn("RAG 用户向量检索失败 keyword={}: {}", query.trim(), e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Long> rankSemanticCandidates(String query, List<Map<String, Object>> candidates) {
        if (query == null || query.trim().isEmpty() || candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("query", query.trim());
            payload.put("candidates", candidates);
            Map<String, Object> data = invokeGateway("SEARCH", "QUERY", null, payload);
            Object results = data.get("candidateResults");
            if (!(results instanceof List<?> list)) {
                return Collections.emptyList();
            }
            List<Long> ranked = new ArrayList<>();
            for (Object item : list) {
                Long id = parseVectorHitId(item, "candidateId", "candidate_id");
                if (id != null && !ranked.contains(id)) {
                    ranked.add(id);
                }
            }
            return ranked;
        } catch (Exception e) {
            log.warn("AI 候选语义排序失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void indexEmojiRag(RagEmojiIndexDTO payload) {
        try {
            invokeGateway("RAG", "INDEX_EMOJI", null, AiHubConverter.ragEmojiIndexToMap(payload));
        } catch (Exception e) {
            log.warn("RAG 表情包索引失败 shopId={}: {}", payload != null ? payload.getShopId() : null, e.getMessage());
        }
    }

    @Override
    public void indexMusicRag(RagMusicIndexDTO payload) {
        try {
            invokeGateway("RAG", "INDEX_MUSIC", null, AiHubConverter.ragMusicIndexToMap(payload));
        } catch (Exception e) {
            log.warn("RAG 曲目索引失败 musicKey={}: {}", payload != null ? payload.getMusicKey() : null, e.getMessage());
        }
    }

    @Override
    public String validateText(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("content", content);
            Map<String, Object> data = invokeGateway("CONTENT_MODERATION", "TEXT_AUDIT", null, payload);
            if (Boolean.TRUE.equals(data.get("allowed"))) {
                return null;
            }
            Object reason = data.get("reason");
            return reason != null ? String.valueOf(reason) : "内容审核未通过";
        } catch (ApplicationException e) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_CHECK_CONTENT_ERROR));
        }
    }

    @Override
    public boolean validateImageUrl(String imageUrl, String objectKey) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }
        log.info("图片审核 URL url={} objectKey={}",
                imageUrl.length() > 120 ? imageUrl.substring(0, 120) + "..." : imageUrl,
                objectKey != null && objectKey.length() > 80 ? objectKey.substring(0, 80) + "..." : objectKey);
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("imageUrl", imageUrl.trim());
            if (objectKey != null && !objectKey.isBlank()) {
                payload.put("objectKey", objectKey.trim());
            }
            Map<String, Object> data = invokeGateway("CONTENT_MODERATION", "IMAGE_AUDIT", null, payload);
            return Boolean.TRUE.equals(data.get("allowed"));
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("图片 AI 审核不可用: {}", e.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_CHECK_IMAGE_ERROR));
        }
    }

    @Override
    public List<AiImageModerationItemResultVO> validateImageUrls(AiImageModerationBatchUrlRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            return Collections.emptyList();
        }
        List<AiImageModerationBatchUrlRequest.Item> items = request.getItems();
        if (items.size() > 9) {
            items = items.subList(0, 9);
        }
        log.info("批量图片审核 count={}", items.size());
        try {
            Map<String, Object> payload = new HashMap<>();
            List<Map<String, Object>> itemMaps = new ArrayList<>(items.size());
            for (AiImageModerationBatchUrlRequest.Item item : items) {
                Map<String, Object> row = new HashMap<>();
                String imageUrl = item != null && item.getImageUrl() != null ? item.getImageUrl().trim() : "";
                String objectKey = item != null && item.getObjectKey() != null ? item.getObjectKey().trim() : "";
                row.put("imageUrl", imageUrl);
                row.put("objectKey", objectKey);
                itemMaps.add(row);
            }
            payload.put("items", itemMaps);
            Map<String, Object> data = invokeGateway("CONTENT_MODERATION", "IMAGE_AUDIT", null, payload);
            Object resultsObj = data != null ? data.get("results") : null;
            if (resultsObj instanceof List<?> resultsList && !resultsList.isEmpty()) {
                return parseImageModerationResults(resultsList, items);
            }
            log.warn("批量图片审核未返回 results，回退逐张审核 count={}", items.size());
            return fallbackValidateImageUrls(items);
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("批量图片 AI 审核不可用: {}", e.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_CHECK_IMAGE_ERROR));
        }
    }

    private List<AiImageModerationItemResultVO> parseImageModerationResults(
            List<?> resultsList,
            List<AiImageModerationBatchUrlRequest.Item> items) {
        List<AiImageModerationItemResultVO> vos = new ArrayList<>(resultsList.size());
        for (Object raw : resultsList) {
            if (!(raw instanceof Map<?, ?> map)) {
                continue;
            }
            AiImageModerationItemResultVO vo = new AiImageModerationItemResultVO();
            Integer index = integerValue(map.get("index"));
            vo.setIndex(index != null ? index : vos.size());
            vo.setAllowed(Boolean.TRUE.equals(map.get("allowed")));
            Object reason = map.get("reason");
            vo.setReason(reason != null ? String.valueOf(reason) : null);
            Object imageUrl = map.get("imageUrl");
            Object objectKey = map.get("objectKey");
            if (imageUrl != null) {
                vo.setImageUrl(String.valueOf(imageUrl));
            } else if (vo.getIndex() >= 0 && vo.getIndex() < items.size()) {
                AiImageModerationBatchUrlRequest.Item item = items.get(vo.getIndex());
                vo.setImageUrl(item != null ? item.getImageUrl() : null);
            }
            if (objectKey != null) {
                vo.setObjectKey(String.valueOf(objectKey));
            } else if (vo.getIndex() >= 0 && vo.getIndex() < items.size()) {
                AiImageModerationBatchUrlRequest.Item item = items.get(vo.getIndex());
                vo.setObjectKey(item != null ? item.getObjectKey() : null);
            }
            vos.add(vo);
        }
        return vos;
    }

    private List<AiImageModerationItemResultVO> fallbackValidateImageUrls(
            List<AiImageModerationBatchUrlRequest.Item> items) {
        List<AiImageModerationItemResultVO> vos = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            AiImageModerationBatchUrlRequest.Item item = items.get(i);
            AiImageModerationItemResultVO vo = new AiImageModerationItemResultVO();
            vo.setIndex(i);
            vo.setImageUrl(item != null ? item.getImageUrl() : null);
            vo.setObjectKey(item != null ? item.getObjectKey() : null);
            try {
                boolean allowed = validateImageUrl(
                        item != null ? item.getImageUrl() : null,
                        item != null ? item.getObjectKey() : null);
                vo.setAllowed(allowed);
                if (!allowed) {
                    vo.setReason(ResultCode.FAILED_IMAGE_VIOLATION.getMessage());
                }
            } catch (ApplicationException exception) {
                vo.setAllowed(false);
                vo.setReason(exception.getMessage() != null
                        ? exception.getMessage()
                        : ResultCode.FAILED_AI_CHECK_IMAGE_ERROR.getMessage());
            } catch (Exception exception) {
                vo.setAllowed(false);
                vo.setReason(ResultCode.FAILED_AI_CHECK_IMAGE_ERROR.getMessage());
            }
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public boolean validateImagePayload(String contentBase64, String filename, String contentType) {
        if (contentBase64 == null || contentBase64.isBlank()) {
            return false;
        }
        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(contentBase64);
        } catch (IllegalArgumentException exception) {
            log.warn("图片 Base64 解码失败 filename={} b64Len={}", filename, contentBase64.length());
            throw new ApplicationException(
                    Result.fail(ResultCode.FAILED_IMAGE_FORMAT_UNSUPPORTED, "图片数据不完整，请重试"));
        }
        if (imageBytes.length < 32) {
            log.warn("图片字节过短 filename={} bytes={} b64Len={}", filename, imageBytes.length, contentBase64.length());
            throw new ApplicationException(
                    Result.fail(ResultCode.FAILED_IMAGE_FORMAT_UNSUPPORTED, "图片数据不完整，请重试"));
        }
        log.warn("图片审核载荷 filename={} bytes={}KB b64Len={} head={}",
                filename, imageBytes.length / 1024, contentBase64.length(), hexHead(imageBytes, 8));
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("contentBase64", Base64.getEncoder().encodeToString(imageBytes));
            payload.put("filename", filename);
            payload.put("contentType", contentType != null && !contentType.isBlank() ? contentType : "image/jpeg");
            Map<String, Object> data = invokeGateway("CONTENT_MODERATION", "IMAGE_AUDIT", null, payload);
            return Boolean.TRUE.equals(data.get("allowed"));
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("图片 AI 审核不可用: {}", e.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_CHECK_IMAGE_ERROR));
        }
    }

    @Override
    public AiGobangMoveVO chooseGobangMove(AiGobangMoveRequest request) {
        if (request == null || request.getBoard() == null || request.getBoard().length == 0) {
            return null;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("board", request.getBoard());
            payload.put("aiChess", request.getAiChess());
            payload.put("modelCode", request.getModelCode());
            payload.put("useLlm", true);
            if (request.getInsight() != null) {
                payload.put("insight", request.getInsight());
            }
            Map<String, Object> data = invokeGateway("GAME", "GOBANG_MOVE", null, payload);
            AiGobangMoveVO vo = new AiGobangMoveVO();
            vo.setRow(integerValue(data.get("row")));
            vo.setCol(integerValue(data.get("col")));
            Object modelCode = data.get("modelCode") != null ? data.get("modelCode") : data.get("model");
            vo.setModelCode(modelCode != null ? String.valueOf(modelCode) : null);
            vo.setFallback(Boolean.TRUE.equals(data.get("fallback")));
            return vo;
        } catch (Exception e) {
            log.warn("五子棋 AI 调用失败: {}", e.getMessage());
            return null;
        }
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.valueOf(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Object searchResults(String scope, Map<String, Object> payload) {
        payload.put("scope", scope);
        Map<String, Object> data = invokeGateway("SEARCH", "QUERY", null, payload);
        if ("ARTICLE".equals(scope)) {
            return data.get("articleResults");
        }
        if ("EMOJI".equals(scope)) {
            return data.get("emojiResults");
        }
        if ("MUSIC".equals(scope)) {
            return data.get("musicResults");
        }
        return data.get("userResults");
    }

    private static String parseVectorHitMusicKey(Object item) {
        if (!(item instanceof Map<?, ?> map)) {
            return null;
        }
        Object key = map.get("musicKey");
        if (key == null) {
            key = map.get("music_key");
        }
        if (key == null) {
            return null;
        }
        String text = String.valueOf(key).trim();
        return text.isEmpty() ? null : text;
    }

    private static String hexHead(byte[] bytes, int length) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        int end = Math.min(length, bytes.length);
        StringBuilder builder = new StringBuilder(end * 2);
        for (int i = 0; i < end; i++) {
            builder.append(String.format("%02x", bytes[i]));
        }
        return builder.toString();
    }
}
