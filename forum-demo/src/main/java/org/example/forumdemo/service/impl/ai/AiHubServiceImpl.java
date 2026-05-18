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

import java.util.HashMap;
import java.util.LinkedHashMap;
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
            Object codeObj = resp.get("code");
            int code = codeObj instanceof Number ? ((Number) codeObj).intValue() : -1;
            if (code != 200) {
                String msg = resp.get("msg") != null ? String.valueOf(resp.get("msg")) : "ai hub error";
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
            log.warn("AI Hub 调用失败: {}", e.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_ENGINE));
        }
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
}
