package org.pluchon.forum.service.impl.ai;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.converter.AiHubConverter;
import org.pluchon.forum.api.ai.AiGobangMoveRequest;
import org.pluchon.forum.api.ai.AiGobangMoveVO;
import org.pluchon.forum.entity.dto.ai.AiCoverHintsRequest;
import org.pluchon.forum.entity.dto.ai.AiImageRequest;
import org.pluchon.forum.entity.dto.ai.AiPolishRequest;
import org.pluchon.forum.entity.dto.ai.AiRecommendationArticleFeatureRequest;
import org.pluchon.forum.entity.dto.ai.AiRecommendationProfileRequest;
import org.pluchon.forum.entity.dto.ai.RagArticleIndexDTO;
import org.pluchon.forum.entity.dto.ai.RagUserIndexDTO;
import org.pluchon.forum.entity.vo.ai.AiHubCoverHintsResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubImageResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubPolishResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationFeatureResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationProfileResultVO;
import org.pluchon.forum.entity.vo.ai.RagArticleVectorHitVO;
import org.pluchon.forum.entity.vo.ai.RagUserVectorHitVO;
import org.pluchon.forum.service.interfaces.ai.AiHubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class AiHubServiceImpl implements AiHubService {

    /** 站点搜索：帖子向量最低相关度（与 ai-server rag.vector_min_score 对齐） */
    private static final double ARTICLE_SEARCH_MIN_SCORE = 0.20;
    /** 站点搜索：用户向量最低相关度 */
    private static final double USER_SEARCH_MIN_SCORE = 0.28;

    @Value("${forum.ai.hub-base-url:http://localhost:5000}")
    private String hubBaseUrl;

    @Value("${forum.ai.internal-key:}")
    private String internalKey;

    @Autowired
    private RestTemplate forumRestTemplate;

    private String joinUrl(String path) {
        String base = hubBaseUrl.endsWith("/") ? hubBaseUrl.substring(0, hubBaseUrl.length() - 1) : hubBaseUrl;
        return base + path;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalKey != null && !internalKey.isBlank()) {
            headers.set("X-Internal-Key", internalKey);
        }
        return headers;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        return postJson(forumRestTemplate, path, body);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> postJson(RestTemplate restTemplate, String path, Map<String, Object> body) {
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, jsonHeaders());
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(joinUrl(path), entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("bad http status");
            }
            Map resp = response.getBody();
            int code = parseHubCode(resp.get("code"));
            if (code != 200) {
                String msg = resp.get("msg") != null ? String.valueOf(resp.get("msg")) : "ai hub error";
                log.warn("AI Hub 业务码异常 path={} code={} msg={}", path, code, msg);
                throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_ENGINE, msg));
            }
            Object data = resp.get("data");
            if (data instanceof Map) {
                return (Map<String, Object>) data;
            }
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("payload", data);
            return wrap;
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI Hub 调用失败 path={}: {}", path, e.getMessage(), e);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_ENGINE,
                    "AI 服务调用失败: " + (e.getMessage() != null ? e.getMessage() : "unknown")));
        }
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeGateway(String taskType, String intent, Long userId, Map<String, Object> payload) {
        Map<String, Object> body = new HashMap<>();
        body.put("taskType", taskType);
        body.put("intent", intent);
        body.put("version", "v1");
        body.put("userContext", Collections.singletonMap("userId", userId));
        body.put("payload", payload);
        Map<String, Object> gateway = postJson("/api/v1/gateway/invoke", body);
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
        payload.put("kind", request.getKind());
        payload.put("title", request.getTitle());
        payload.put("content", request.getContent());
        payload.put("editorMode", request.getEditorMode());
        return AiHubConverter.toPolishResult(invokeGateway("POST_CREATION", "POLISH", userId, payload));
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
    @SuppressWarnings({"rawtypes", "unchecked"})
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
                log.info("RAG 帖子向量无有效结果 keyword={}", query.trim());
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
                log.info("RAG 帖子向量召回为空 keyword={}", query.trim());
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
                // try next key
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
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
    @SuppressWarnings({"rawtypes", "unchecked"})
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
    @SuppressWarnings({"rawtypes", "unchecked"})
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
    @SuppressWarnings({"rawtypes", "unchecked"})
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
    public boolean validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("contentBase64", Base64.getEncoder().encodeToString(file.getBytes()));
            payload.put("filename", file.getOriginalFilename());
            payload.put("contentType", file.getContentType());
            Map<String, Object> data = invokeGateway("CONTENT_MODERATION", "IMAGE_AUDIT", null, payload);
            return Boolean.TRUE.equals(data.get("allowed"));
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

    @SuppressWarnings("unchecked")
    private Object searchResults(String scope, Map<String, Object> payload) {
        payload.put("scope", scope);
        Map<String, Object> data = invokeGateway("SEARCH", "QUERY", null, payload);
        return "ARTICLE".equals(scope) ? data.get("articleResults") : data.get("userResults");
    }
}
