package org.example.forumdemo.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.config.AiHubUrls;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// AI 模块
@Slf4j
public class AiAuditUtils {

    /**
     * 图片安全审核方法（同步 HTTP 请求）
     * 作用：接收前端上传的图片，通过 HTTP 协议以表单形式包装并转发给 Python AI 视觉模型进行风控检测
     *
     * @param file 前端上传的图片文件对象
     * @return boolean true表示图片安全合规，允许发布；false表示违规
     */
    public static boolean isImageAllowed(MultipartFile file) {
        try {
            // 1. 创建 Spring 提供的用于发起同步 HTTP 请求的 RestTemplate 客户端实例
            RestTemplate restTemplate = new RestTemplate();
            // 2. 将 MultipartFile 图片直接转换为符合 HTTP 表单协议的内存数据流结构
            MultiValueMap<String, Object> body = getStringObjectMultiValueMap(file);
            // 3. 创建 HTTP 请求头对象
            HttpHeaders headers = new HttpHeaders();
            // 4. 显式声明该请求的 Content-Type 为 multipart/form-data（文件上传表单格式）
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            // 5. 将表单数据体（body）和请求头（headers）封装成一个完整的 HTTP 请求实体对象
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            // 6. 发起同步 POST 请求，将图片数据投递至 Python AI 图片风控地址，并要求将响应反序列化为 Map 对象
            ResponseEntity<Map> response = restTemplate.postForEntity(AiHubUrls.validateImageUrl(), requestEntity, Map.class);
            // 7. 安全校验：确保 HTTP 状态码为 200 OK 且响应体不为空
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // 8. 从 Python 返回的响应中提取键名为 "allow" 的结果值
                Object allowObj = response.getBody().get("allow");
                // 9. 如果该值是布尔类型，则将其强转并作为审核结论返回
                if (allowObj instanceof Boolean) {
                    return (Boolean) allowObj;
                }
            }
            // 10. 默认安全放行：若返回格式不匹配，默认放行防止卡死业务
            return true;
        } catch (Exception e) {
            // 11. 强风控机制：若 AI 审核接口发生超时或失败，为保证内容安全，打印警告并抛出全局业务异常
            log.warn("图片 AI 审核不可用: {}", e.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_CHECK_IMAGE_ERROR));
        }
    }

    /**
     * 将 Spring 的 MultipartFile 在纯内存中转换为符合 HTTP 协议文件上传格式的表单参数
     * 优势：纯内存操作，彻底免去本地临时磁盘文件的创建与销毁开销，性能极佳
     */
    private static @NonNull MultiValueMap<String, Object> getStringObjectMultiValueMap(MultipartFile file) throws IOException {
        // 1. 从前端文件对象中读取文件的原始二进制字节数组
        byte[] fileBytes = file.getBytes();
        // 2. 匿名覆写 ByteArrayResource 类，使其具备动态获取文件名的能力（符合 HTTP 文件表单协议必须有文件名的规范）
        ByteArrayResource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                // 3. 获取图片原始名称，如果为空，则默认命名为 upload.jpg
                String original = file.getOriginalFilename();
                return (original != null && !original.isEmpty()) ? original : "upload.jpg";
            }
        };
        // 4. 创建 Spring 专用的表单数据映射对象（一键多值的 Map 结构）
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // 5. 将封装好的内存图片资源放入 Map，其中的 Key 值 "file" 必须与 Python AI 接收端的参数字段名保持高度一致
        body.add("file", resource);
        // 6. 返回组装完毕的表单数据体
        return body;
    }

    /**
     * 文本内容安全风控检测（同步 HTTP 请求）
     * 作用：检测帖子、评论等文本是否包含政治、暴恐、色情或辱骂等违规内容
     *
     * @param content 需要检测的纯文本内容
     * @return String 如果内容合规，返回 null；如果内容违规，返回具体的违规原因描述
     */
    public static String isTextAllowed(String content) {
        // 1. 安全校验：如果文本内容为空，直接判定安全，无需发起网络调用
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        try {
            // 2. 实例化 HTTP 请求模板客户端
            RestTemplate restTemplate = new RestTemplate();
            // 3. 构造请求体 Map，将待检测文本放入 "content" 键中
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("content", content);
            // 4. 创建并设置 HTTP 请求头，声明数据交互格式为 JSON
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 5. 封装为完整的 HTTP 请求对象
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
            // 6. 发起同步 POST 请求，将文本 JSON 投递至 Python AI 文本风控地址
            ResponseEntity<Map> response = restTemplate.postForEntity(AiHubUrls.validateTextUrl(), requestEntity, Map.class);
            // 7. 解析响应结果
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // 8. 提取 Python 返回的 "allow" 审核布尔值
                Boolean allowed = (Boolean) response.getBody().get("allow");
                // 9. 如果 allowed 存在且为 false，说明检测到了违法违规词汇
                if (allowed != null && !allowed) {
                    // 10. 直接将 Python 返回的违规详细原因（如 "包含敏感词：xxx"）提取并返回
                    return (String) response.getBody().get("msg");
                }
            }
        } catch (Exception e) {
            // 11. 强风控安全防御：一旦网络超时或 Python 接口瘫痪，打印日志并抛出业务异常，阻止内容发布
            log.warn("文本 AI 审核不可用: {}", e.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_CHECK_CONTENT_ERROR));
        }
        // 12. 若接口正常且无违规，返回 null 代表完全合规通过
        return null;
    }

    /**
     * RAG 帖子语义重排搜索（同步 HTTP 联调）
     * 作用：传统 SQL 只能精确字面量匹配，此方法将用户搜索词与候选数据发往 Python 模型层进行语义向量匹配，让搜索更具“人工智能”
     *
     * @param query 用户输入的检索词
     * @param candidates 从数据库初筛出来的候选文章元数据列表
     * @return List<Long> 由 AI 计算语义相似度打分后，从高到低重新排序的文章 ID 集合
     */
    @SuppressWarnings("rawtypes")
    public static List<Long> ragSearchArticles(String query, List<Map<String, Object>> candidates) {
        // 1. 边界防御：如果搜索词为空，或数据库没有初筛出候选文章，直接返回空列表
        if (query == null || query.trim().isEmpty() || candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            // 2. 创建 RestTemplate 客户端
            RestTemplate restTemplate = new RestTemplate();
            // 3. 构建发送给 Python 的 JSON 载荷体，包含搜索 query 和候选集合
            Map<String, Object> body = new HashMap<>();
            body.put("query", query);
            body.put("candidates", candidates);
            // 4. 设置 JSON 交互请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 5. 封装请求体
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            // 6. 发起同步 POST 请求，将数据投递给 Python AI 的文章 RAG 匹配引擎
            ResponseEntity<Map> resp = restTemplate.postForEntity(AiHubUrls.articleRagSearchUrl(), req, Map.class);
            // 7. 确保响应状态正常且有返回数据
            if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
                return Collections.emptyList();
            }
            // 8. 提取重排结果列表，结果形如：[{"articleId": 105, "score": 0.98}, {"articleId": 101, ...}]
            Object results = resp.getBody().get("results");
            if (!(results instanceof List<?>)) {
                return Collections.emptyList();
            }
            // 9. 遍历 AI 重排后的结果，将其中的文章 ID 依次按语义相似度从高到低提取出来
            List<Long> sorted = new ArrayList<>();
            for (Object item : (List<?>) results) {
                if (!(item instanceof Map<?, ?> m)){
                    continue;
                }
                Object idObj = m.get("articleId");
                if (idObj == null){
                    continue;
                }
                try {
                    // 10. 安全转换为 Long 型文章主键，添加至有序列表中
                    sorted.add(Long.valueOf(String.valueOf(idObj)));
                } catch (NumberFormatException ignore) {
                    // 容错处理：跳过单条脏数据，不影响整体结果呈现
                }
            }
            // 11. 返回经过 AI 语义完美排序的文章 ID 列表
            return sorted;
        } catch (Exception e) {
            // 12. 柔性降级（Resilience）：RAG 属于体验增值业务，AI 挂了也不能耽误普通搜索，故捕获异常并降级返回空列表
            log.warn("RAG 搜索调用失败, 降级为空结果: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * RAG 帖子语义重排，保留 articleId 与 score（供看板娘「你可能感兴趣」等场景）.
     */
    @SuppressWarnings("rawtypes")
    public static List<Map<String, Object>> ragSearchArticlesRanked(
            String query, List<Map<String, Object>> candidates) {
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
            ResponseEntity<Map> resp = restTemplate.postForEntity(AiHubUrls.articleRagSearchUrl(), req, Map.class);
            if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
                return Collections.emptyList();
            }
            Object results = resp.getBody().get("results");
            if (!(results instanceof List<?>)) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> ranked = new ArrayList<>();
            for (Object item : (List<?>) results) {
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
                if (scoreObj instanceof Number n) {
                    row.put("score", n.doubleValue());
                } else if (scoreObj != null) {
                    try {
                        row.put("score", Double.parseDouble(String.valueOf(scoreObj)));
                    } catch (NumberFormatException ignore) {
                        row.put("score", 0.0);
                    }
                } else {
                    row.put("score", 0.0);
                }
                ranked.add(row);
            }
            return ranked;
        } catch (Exception e) {
            log.warn("RAG 重排(含分数)调用失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * RAG 用户语义匹配检索（同步 HTTP 联调）
     * 作用：支持根据用户的简介、标签进行智能语义检索，寻找兴趣相投的用户
     *
     * @param query 用户输入的搜索词
     * @param candidates 数据库初筛出来的用户候选简介元数据集合
     * @return List<Long> AI 排序后由高到低的用户 ID 集合
     */
    @SuppressWarnings("rawtypes")
    public static List<Long> ragSearchUsers(String query, List<Map<String, Object>> candidates) {
        // 1. 边界检查：若参数为空则直接返回空集合
        if (query == null || query.trim().isEmpty() || candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            // 2. 实例化客户端并构建 JSON 载荷
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> body = new HashMap<>();
            body.put("query", query);
            body.put("candidates", candidates);
            // 3. 请求头组装
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            // 4. 发起同步请求，投递至 Python AI 的用户 RAG 检索端点
            ResponseEntity<Map> resp = restTemplate.postForEntity(AiHubUrls.userRagSearchUrl(), req, Map.class);
            // 5. 校验返回结果
            if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
                return Collections.emptyList();
            }
            Object results = resp.getBody().get("results");
            if (!(results instanceof List<?>)) {
                return Collections.emptyList();
            }
            // 6. 依次提取打分重排后的用户 ID
            List<Long> sorted = new ArrayList<>();
            for (Object item : (List<?>) results) {
                if (!(item instanceof Map<?, ?> m)) continue;
                Object idObj = m.get("userId");
                if (idObj == null) continue;
                try {
                    sorted.add(Long.valueOf(String.valueOf(idObj)));
                } catch (NumberFormatException ignore) {
                    // 跳过
                }
            }
            return sorted;
        } catch (Exception e) {
            // 7. 柔性降级：用户检索 AI 出错时，平滑降级返回空列表，确保核心业务不中断
            log.warn("用户 RAG 搜索调用失败, 降级为空结果: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * AI 智能提炼文章摘要（同步 HTTP 请求）
     * 作用：调用 Python 大语言模型服务，将数千字的长文智能提炼成 100 字以内的精炼摘要
     *
     * @param content 文章的长篇纯文本内容
     * @return String AI 提炼出的精炼摘要；若失败则返回 null
     */
    public static String getSummary(String content) {
        // 1. 安全校验：如果帖子没有内容，则不发起大模型计算，直接返回空
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        try {
            // 2. 实例化客户端并构建请求 JSON
            RestTemplate restTemplate = new RestTemplate();
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("content", content);
            // 3. 构建 JSON 报文头并打包
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
            // 4. 调用 Python 侧大模型摘要提取 API
            ResponseEntity<Map> response = restTemplate.postForEntity(AiHubUrls.summarizeUrl(), requestEntity, Map.class);
            // 5. 校验响应，成功则从中提取 "summary" 键对应的文本并返回
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object summary = response.getBody().get("summary");
                if (summary instanceof String && !((String) summary).trim().isEmpty()) {
                    return (String) summary;
                }
            }
        } catch (Exception e) {
            // 6. 强规则保障：大模型摘要服务不可用时，抛出对应错误阻断发布流程
            log.warn("AI 摘要服务不可用，跳过摘要生成: {}", e.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_GENERATE_SUMMARY_ERROR));
        }
        return null;
    }
}
