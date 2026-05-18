package org.example.forumdemo.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// AI 审查工具
@Slf4j
public class AiAuditUtils {
    //图片审核，通过HTTP转发给Python
    public static boolean isImageAllowed(MultipartFile file) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            // 将接收到的图片文件转换成内存中的字节数组
            MultiValueMap<String, Object> body = getStringObjectMultiValueMap(file);
            HttpHeaders headers = new HttpHeaders();
            // 声明这是一个文件的上传请求
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            // 自动化构建为HTTP报文格式，并接受 AI 返回的请求
            ResponseEntity<Map> response = restTemplate.postForEntity(Constant.AI_IMAGE_URL, requestEntity, Map.class);
            // 必须保证返回的内容有结果
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object allowObj = response.getBody().get("allow");
                if (allowObj instanceof Boolean) {
                    return (Boolean) allowObj;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("图片 AI 审核不可用，默认放行: {}", e.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_CHECK_IMAGE_ERROR));
        }
    }

    //转换成字节数组封装为一个方法
    private static @NonNull MultiValueMap<String, Object> getStringObjectMultiValueMap(MultipartFile file) throws IOException {
        byte[] fileBytes = file.getBytes();
        // 符合HTTP协议的请求参数
        ByteArrayResource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                String original = file.getOriginalFilename();
                return (original != null && !original.isEmpty()) ? original : "upload.jpg";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // 和 AI 模块约定好的接收的字段名要保持一致
        body.add("file", resource);
        return body;
    }

    //null表示通过，否则就是未通过的原因
    public static String isTextAllowed(String content) {
        //没有内容直接返回
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            Map<String, String> requestBody = new java.util.HashMap<>();
            requestBody.put("content", content);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(Constant.AI_TEXT_URL, requestEntity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Boolean allowed = (Boolean) response.getBody().get("allow");
                if (allowed != null && !allowed) {
                    return (String) response.getBody().get("msg");
                }
            }
        } catch (Exception e) {
            log.warn("文本 AI 审核不可用，默认放行: {}", e.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_CHECK_CONTENT_ERROR));
        }
        return null;
    }

    /**
     * RAG 语义检索: 把 query + 召回侧 candidates 一起丢给 Python AI 服务做 rerank.
     * Java 侧只做"召回 + HTTP 转发", 真正打分由 Python 端 dashscope rerank 完成.
     *
     * 入参:
     *   query      - 用户搜索词
     *   candidates - [{articleId, text}] 召回侧粗筛集合, text 通常是 "title+正文前N字"
     * 出参: 按相关性降序的 articleId 列表; 任何异常一律返回 emptyList, 让调用方降级为"无结果".
     *
     * 与 isImage/isTextAllowed 不同: 搜索失败不阻断主流程, 也不抛异常.
     */
    @SuppressWarnings("rawtypes")
    public static List<Long> ragSearchArticles(String query, List<Map<String, Object>> candidates) {
        if (query == null || query.trim().isEmpty() || candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> body = new HashMap<>();
            body.put("query", query);
            body.put("candidates", candidates);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.postForEntity(Constant.AI_ARTICLE_SEARCH_URL, req, Map.class);
            if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
                return Collections.emptyList();
            }
            Object results = resp.getBody().get("results");
            if (!(results instanceof List<?>)) {
                return Collections.emptyList();
            }
            List<Long> sorted = new ArrayList<>();
            for (Object item : (List<?>) results) {
                if (!(item instanceof Map<?, ?> m)) continue;
                Object idObj = m.get("articleId");
                if (idObj == null) continue;
                try {
                    sorted.add(Long.valueOf(String.valueOf(idObj)));
                } catch (NumberFormatException ignore) {
                    // 跳过坏数据, 不影响其他结果
                }
            }
            return sorted;
        } catch (Exception e) {
            log.warn("RAG 搜索调用失败, 降级为空结果: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 用户 RAG 语义检索: 与 {@link #ragSearchArticles} 相同降级策略, 解析 userId.
     */
    @SuppressWarnings("rawtypes")
    public static List<Long> ragSearchUsers(String query, List<Map<String, Object>> candidates) {
        if (query == null || query.trim().isEmpty() || candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> body = new HashMap<>();
            body.put("query", query);
            body.put("candidates", candidates);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.postForEntity(Constant.AI_USER_SEARCH_URL, req, Map.class);
            if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
                return Collections.emptyList();
            }
            Object results = resp.getBody().get("results");
            if (!(results instanceof List<?>)) {
                return Collections.emptyList();
            }
            List<Long> sorted = new ArrayList<>();
            for (Object item : (List<?>) results) {
                if (!(item instanceof Map<?, ?> m)) continue;
                Object idObj = m.get("userId");
                if (idObj == null) continue;
                try {
                    sorted.add(Long.valueOf(String.valueOf(idObj)));
                } catch (NumberFormatException ignore) {
                    // skip
                }
            }
            return sorted;
        } catch (Exception e) {
            log.warn("用户 RAG 搜索调用失败, 降级为空结果: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 生成帖子摘要
     * @param content 帖子原文（富文本 或 Markdown）
     * @return 摘要字符串，若内容过短或服务异常则返回 null
     */
    public static String getSummary(String content) {
        //依然没有内容直接返回空
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            Map<String, String> requestBody = new java.util.HashMap<>();
            requestBody.put("content", content);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(Constant.AI_SUMMARY_URL, requestEntity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object summary = response.getBody().get("summary");
                if (summary instanceof String && !((String) summary).trim().isEmpty()) {
                    return (String) summary;
                }
            }
        } catch (Exception e) {
            log.warn("AI 摘要服务不可用，跳过摘要生成: {}", e.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_GENERATE_SUMMARY_ERROR));
        }
        return null;
    }
}
