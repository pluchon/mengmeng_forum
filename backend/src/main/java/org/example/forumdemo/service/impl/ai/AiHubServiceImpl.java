package org.example.forumdemo.service.impl.ai;

import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.dto.ai.AiCoverHintsRequest;
import org.example.forumdemo.entity.dto.ai.AiImageRequest;
import org.example.forumdemo.entity.dto.ai.AiWriteRequest;
import org.example.forumdemo.service.interfaces.ai.AiHubService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiHubServiceImpl implements AiHubService {

    @Value("${forum.ai.hub-base-url:http://localhost:5000}")
    private String hubBaseUrl;

    @Value("${forum.ai.internal-key:}")
    private String internalKey;

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
        RestTemplate restTemplate = new RestTemplate();
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

    @Override
    public Map<String, Object> write(Long userId, AiWriteRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("kind", request.getKind());
        body.put("messages", request.getMessages());
        return postJson("/api/v1/ai/write", body);
    }

    @Override
    public Map<String, Object> coverHints(Long userId, AiCoverHintsRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("article_text", request.getArticleText());
        return postJson("/api/v1/ai/cover-hints", body);
    }

    @Override
    public Map<String, Object> image(Long userId, AiImageRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("prompt", request.getPrompt());
        body.put("quality", request.getQuality());
        return postJson("/api/v1/ai/image", body);
    }

    @Override
    public void indexArticleRag(Map<String, Object> payload) {
        try {
            postJson("/api/v1/rag/index-article", payload);
        } catch (Exception e) {
            log.warn("RAG 帖子索引失败 articleId={}: {}", payload.get("articleId"), e.getMessage());
        }
    }

    @Override
    public void indexUserRag(Map<String, Object> payload) {
        try {
            postJson("/api/v1/rag/index-user", payload);
        } catch (Exception e) {
            log.warn("RAG 用户索引失败 userId={}: {}", payload.get("userId"), e.getMessage());
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
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, jsonHeaders());
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    joinUrl("/api/v1/rag/article-vector-search"), req, Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("RAG 帖子向量 HTTP 异常 status={}", resp.getStatusCode());
                return Collections.emptyList();
            }
            Map<String, Object> respBody = resp.getBody();
            int code = parseHubCode(respBody.get("code"));
            if (code != 200) {
                log.warn("RAG 帖子向量业务码异常 code={} msg={}", code, respBody.get("msg"));
                return Collections.emptyList();
            }
            Object results = respBody.get("results");
            if (!(results instanceof List<?> list)) {
                log.info("RAG 帖子向量无 results 字段 keyword={} msg={}", query.trim(), respBody.get("msg"));
                return Collections.emptyList();
            }
            List<Long> sorted = new ArrayList<>();
            for (Object item : list) {
                Long id = parseVectorHitId(item, "articleId", "article_id");
                if (id != null) {
                    sorted.add(id);
                }
            }
            if (sorted.isEmpty()) {
                log.info("RAG 帖子向量召回为空 keyword={} msg={}", query.trim(), respBody.get("msg"));
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
    public List<Map<String, Object>> ragArticleVectorRanked(String query, List<Map<String, Object>> candidates) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", query.trim());
            body.put("candidates", candidates != null ? candidates : Collections.emptyList());
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, jsonHeaders());
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    joinUrl("/api/v1/rag/article-vector-search"), req, Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return Collections.emptyList();
            }
            Object results = resp.getBody().get("results");
            if (!(results instanceof List<?> list)) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> ranked = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                Object idObj = m.get("articleId");
                if (idObj == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("articleId", idObj);
                Object scoreObj = m.get("score");
                double score = scoreObj instanceof Number n ? n.doubleValue() : 0.0;
                row.put("score", score);
                ranked.add(row);
            }
            ranked.sort((a, b) -> Double.compare(
                    ((Number) b.getOrDefault("score", 0)).doubleValue(),
                    ((Number) a.getOrDefault("score", 0)).doubleValue()));
            return ranked;
        } catch (Exception e) {
            log.warn("RAG 向量检索(含分数)失败: {}", e.getMessage());
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
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, jsonHeaders());
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    joinUrl("/api/v1/rag/user-vector-search"), req, Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return Collections.emptyList();
            }
            Object results = resp.getBody().get("results");
            if (!(results instanceof List<?> list)) {
                return Collections.emptyList();
            }
            List<Long> sorted = new ArrayList<>();
            for (Object item : list) {
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
}
