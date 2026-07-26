package org.example.forumdemo.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.config.AiHubUrls;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// AI 审核、摘要和 RAG 的兼容调用门面；只与统一 Gateway 通信
@Slf4j
public final class AiAuditUtils {

    private static RestTemplate restTemplate = new RestTemplate();
    private static String internalKey = "";

    private AiAuditUtils() {
    }

    public static void setRestTemplate(RestTemplate template) {
        if (template != null) {
            restTemplate = template;
        }
    }

    public static void setInternalKey(String value) {
        internalKey = value != null ? value.trim() : "";
    }

    public static boolean isImageAllowed(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return false;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("contentBase64", Base64.getEncoder().encodeToString(file.getBytes()));
            payload.put("filename", file.getOriginalFilename());
            payload.put("contentType", file.getContentType());
            Map<String, Object> data = invoke("CONTENT_MODERATION", "IMAGE_AUDIT", payload);
            return Boolean.TRUE.equals(data.get("allowed"));
        } catch (Exception e) {
            log.warn("图片 AI 审核不可用: {}", e.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_CHECK_IMAGE_ERROR));
        }
    }

    public static String isTextAllowed(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("content", content);
            Map<String, Object> data = invoke("CONTENT_MODERATION", "TEXT_AUDIT", payload);
            if (!Boolean.TRUE.equals(data.get("allowed"))) {
                Object reason = data.get("reason");
                return reason != null ? String.valueOf(reason) : "内容审核未通过";
            }
            return null;
        } catch (Exception e) {
            log.warn("文本 AI 审核不可用: {}", e.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_CHECK_CONTENT_ERROR));
        }
    }

    public static List<Long> ragSearchArticles(String query, List<Map<String, Object>> candidates) {
        return extractIds(ragSearch("ARTICLE", query, candidates), "articleId");
    }

    public static List<Map<String, Object>> ragSearchArticlesRanked(String query, List<Map<String, Object>> candidates) {
        return ragSearch("ARTICLE", query, candidates);
    }

    public static List<Map<String, Object>> ragSearchUsersRanked(String query, List<Map<String, Object>> candidates) {
        return ragSearch("USER", query, candidates);
    }

    public static List<Long> ragSearchUsers(String query, List<Map<String, Object>> candidates) {
        return extractIds(ragSearch("USER", query, candidates), "userId");
    }

    public static String getSummary(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("content", content);
            Object summary = invoke("POST_SUMMARY", "GENERATE", payload).get("summary");
            return summary != null ? String.valueOf(summary).trim() : null;
        } catch (Exception e) {
            log.warn("AI 摘要服务不可用: {}", e.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_GENERATE_SUMMARY_ERROR));
        }
    }

    private static List<Map<String, Object>> ragSearch(
            String scope, String query, List<Map<String, Object>> candidates) {
        if (query == null || query.isBlank() || candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("scope", scope);
            payload.put("query", query.trim());
            payload.put("candidates", candidates);
            Object results = invoke("SEARCH", "QUERY", payload).get(
                    "ARTICLE".equals(scope) ? "articleResults" : "userResults");
            if (!(results instanceof List<?> rows)) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Object row : rows) {
                if (row instanceof Map<?, ?> map) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    map.forEach((key, value) -> item.put(String.valueOf(key), value));
                    normalized.add(item);
                }
            }
            return normalized;
        } catch (Exception e) {
            log.warn("RAG {} 检索失败，降级为空结果: {}", scope, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static List<Long> extractIds(List<Map<String, Object>> rows, String idKey) {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object raw = row.get(idKey);
            if (raw instanceof Number number) {
                ids.add(number.longValue());
            } else if (raw != null) {
                try {
                    ids.add(Long.parseLong(String.valueOf(raw)));
                } catch (NumberFormatException ignored) {
                    // 忽略单条脏结果，保留其余检索命中。
                }
            }
        }
        return ids;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map<String, Object> invoke(String taskType, String intent, Map<String, Object> payload) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("taskType", taskType);
        request.put("intent", intent);
        request.put("version", "v1");
        request.put("userContext", Collections.emptyMap());
        request.put("payload", payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!internalKey.isBlank()) {
            headers.set("X-Internal-Key", internalKey);
        }
        ResponseEntity<Map> response = restTemplate.postForEntity(
                AiHubUrls.gatewayInvokeUrl(), new HttpEntity<>(request, headers), Map.class);
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null
                || !(response.getBody().get("data") instanceof Map gateway)
                || !Boolean.TRUE.equals(gateway.get("success"))) {
            throw new IllegalStateException("AI Gateway 调用失败");
        }
        Object data = gateway.get("data");
        if (!(data instanceof Map result)) {
            throw new IllegalStateException("AI Gateway 返回格式异常");
        }
        return (Map<String, Object>) result;
    }
}
