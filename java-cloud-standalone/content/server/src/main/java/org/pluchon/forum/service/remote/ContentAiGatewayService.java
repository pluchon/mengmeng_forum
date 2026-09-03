package org.pluchon.forum.service.remote;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.pluchon.forum.api.ai.AiRagSearchRequest;
import org.pluchon.forum.common.config.OssConfig;
import org.pluchon.forum.common.constant.OssPaths;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.content.client.ContentAiHubInternalFeignClient;
import org.pluchon.forum.entity.dto.AiImageModerationBatchUrlRequest;
import org.pluchon.forum.entity.dto.AiImageModerationUrlRequest;
import org.pluchon.forum.entity.dto.AiArticleTagRecommendRequest;
import org.pluchon.forum.entity.dto.AiMusicRecommendRequest;
import org.pluchon.forum.entity.dto.AiMusicSearchRequest;
import org.pluchon.forum.entity.dto.AiMusicTasteRecommendRequest;
import org.pluchon.forum.entity.dto.AiArticleTagSimilarityRequest;
import org.pluchon.forum.entity.dto.AiCreatorInsightRequest;
import org.pluchon.forum.entity.dto.AiRecommendationArticleFeatureRequest;
import org.pluchon.forum.entity.dto.AiRecommendationProfileRequest;
import org.pluchon.forum.entity.dto.RagArticleIndexDTO;
import org.pluchon.forum.entity.dto.RagMusicIndexDTO;
import org.pluchon.forum.entity.vo.ai.AiHubArticleTagRecommendResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubMusicMatchResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubArticleTagSimilarityResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubCreatorInsightResultVO;
import org.pluchon.forum.entity.vo.ai.AiImageModerationItemResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationFeatureResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationProfileResultVO;
import org.pluchon.forum.entity.vo.ai.RagArticleVectorHitVO;
import org.pluchon.forum.entity.vo.ai.RagUserVectorHitVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// 内容服务访问 AI 网关的本地适配
@Service
public class ContentAiGatewayService {

    private static final Logger log = LoggerFactory.getLogger(ContentAiGatewayService.class);

    private static final String RESOURCE_FAST = "content.ai.fast";
    private static final String RESOURCE_STANDARD = "content.ai.standard";
    private static final String RESOURCE_INDEX = "content.ai.index";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private ContentAiHubInternalFeignClient contentAiHubInternalFeignClient;

    @Autowired
    private OssConfig ossConfig;

    public String validateText(String content) {
        return invoke(RESOURCE_FAST, () -> contentAiHubInternalFeignClient.validateText(content));
    }

    // 上传审图：校验本站 OSS pending URL，并携带 objectKey 供 Python SDK 直读
    public boolean validateImageUrl(String imageUrl, String businessPath, String objectKey) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }
        if (!ossConfig.matchesPublicObjectUrlForAudit(imageUrl, businessPath)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片地址无效"));
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片对象 key 无效"));
        }
        String pendingPrefix = ossConfig.objectKeyPrefix(OssPaths.pendingFolder(businessPath));
        if (!objectKey.startsWith(pendingPrefix)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "仅允许审图 pending 对象"));
        }
        try {
            String keyFromUrl = ossConfig.objectKeyFromPublicUrl(imageUrl.trim());
            if (!objectKey.equals(keyFromUrl)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片地址与对象 key 不一致"));
            }
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片地址无法解析"));
        }
        log.info("审图 URL 请求 businessPath={} objectKey={}", businessPath, objectKey);
        AiImageModerationUrlRequest request = new AiImageModerationUrlRequest();
        request.setImageUrl(imageUrl.trim());
        request.setObjectKey(objectKey);
        return Boolean.TRUE.equals(invoke(
                RESOURCE_FAST,
                () -> contentAiHubInternalFeignClient.validateImageUrl(request)));
    }

    // 批量上传审图：逐项校验 pending key 后一次 Feign 批量调用
    public List<AiImageModerationItemResultVO> validateImageUrls(List<PendingImageAuditItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        if (items.size() > 9) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "单次最多审核9张图片"));
        }
        AiImageModerationBatchUrlRequest request = new AiImageModerationBatchUrlRequest();
        List<AiImageModerationBatchUrlRequest.Item> payloadItems = new ArrayList<>(items.size());
        for (PendingImageAuditItem item : items) {
            if (item == null || item.imageUrl() == null || item.imageUrl().isBlank()) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片地址无效"));
            }
            if (item.objectKey() == null || item.objectKey().isBlank()) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片对象 key 无效"));
            }
            if (item.businessPath() == null || item.businessPath().isBlank()) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "业务路径无效"));
            }
            String imageUrl = item.imageUrl().trim();
            String objectKey = item.objectKey().trim();
            String businessPath = item.businessPath();
            if (!ossConfig.matchesPublicObjectUrlForAudit(imageUrl, businessPath)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片地址无效"));
            }
            String pendingPrefix = ossConfig.objectKeyPrefix(OssPaths.pendingFolder(businessPath));
            if (!objectKey.startsWith(pendingPrefix)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "仅允许审图 pending 对象"));
            }
            try {
                String keyFromUrl = ossConfig.objectKeyFromPublicUrl(imageUrl);
                if (!objectKey.equals(keyFromUrl)) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片地址与对象 key 不一致"));
                }
            } catch (IllegalArgumentException exception) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片地址无法解析"));
            }
            AiImageModerationBatchUrlRequest.Item payloadItem = new AiImageModerationBatchUrlRequest.Item();
            payloadItem.setImageUrl(imageUrl);
            payloadItem.setObjectKey(objectKey);
            payloadItems.add(payloadItem);
        }
        request.setItems(payloadItems);
        log.info("批量审图 URL 请求 count={}", payloadItems.size());
        List<AiImageModerationItemResultVO> results = invoke(
                RESOURCE_FAST,
                () -> contentAiHubInternalFeignClient.validateImageUrls(request));
        return results != null ? results : Collections.emptyList();
    }

    // 批量审图入参：公网 URL + pending objectKey + 业务目录
    public record PendingImageAuditItem(String imageUrl, String objectKey, String businessPath) {
    }

    public String summarize(String content) {
        return invoke(RESOURCE_STANDARD, () -> contentAiHubInternalFeignClient.summarize(content));
    }

    public List<Long> rankSemanticCandidates(String query, List<Map<String, Object>> candidates) {
        AiRagSearchRequest request = new AiRagSearchRequest();
        request.setQuery(query);
        request.setCandidates(candidates);
        return invoke(RESOURCE_STANDARD, () -> contentAiHubInternalFeignClient.rankSemanticCandidates(request));
    }

    public AiHubArticleTagRecommendResultVO recommendArticleTags(AiArticleTagRecommendRequest request) {
        return invoke(RESOURCE_STANDARD, () -> contentAiHubInternalFeignClient.recommendArticleTags(request));
    }

    public AiHubArticleTagSimilarityResultVO checkArticleTagSimilarity(
            AiArticleTagSimilarityRequest request) {
        return invoke(RESOURCE_FAST, () -> contentAiHubInternalFeignClient.checkArticleTagSimilarity(request));
    }

    public AiHubMusicMatchResultVO recommendMusic(AiMusicRecommendRequest request) {
        return invoke(RESOURCE_STANDARD, () -> contentAiHubInternalFeignClient.recommendMusic(request));
    }

    public AiHubMusicMatchResultVO searchMusic(AiMusicSearchRequest request) {
        return invoke(RESOURCE_STANDARD, () -> contentAiHubInternalFeignClient.searchMusic(request));
    }

    public AiHubCreatorInsightResultVO generateCreatorInsight(AiCreatorInsightRequest request) {
        return invoke(RESOURCE_STANDARD, () -> contentAiHubInternalFeignClient.generateCreatorInsight(request));
    }

    public List<Long> ragVectorSearchArticles(String query, List<Map<String, Object>> candidates) {
        AiRagSearchRequest request = new AiRagSearchRequest();
        request.setQuery(query);
        request.setCandidates(candidates);
        return invoke(RESOURCE_STANDARD, () -> contentAiHubInternalFeignClient.ragVectorSearchArticles(request));
    }

    public List<RagArticleVectorHitVO> ragArticleVectorRanked(AiRagSearchRequest request) {
        return invoke(RESOURCE_STANDARD, () -> contentAiHubInternalFeignClient.ragArticleVectorRanked(request));
    }

    public List<RagUserVectorHitVO> ragUserVectorRanked(String query) {
        return invoke(RESOURCE_STANDARD, () -> contentAiHubInternalFeignClient.ragUserVectorRanked(query));
    }

    public AiRecommendationFeatureResultVO generateRecommendationArticleFeature(
            AiRecommendationArticleFeatureRequest request) {
        return invoke(
                RESOURCE_STANDARD,
                () -> contentAiHubInternalFeignClient.generateRecommendationArticleFeature(request));
    }

    public AiRecommendationProfileResultVO generateRecommendationProfile(
            Long userId,
            AiRecommendationProfileRequest request) {
        return invoke(
                RESOURCE_STANDARD,
                () -> contentAiHubInternalFeignClient.generateRecommendationProfile(userId, request));
    }

    public AiHubMusicMatchResultVO recommendMusicTaste(Long userId, AiMusicTasteRecommendRequest request) {
        return invoke(
                RESOURCE_STANDARD,
                () -> contentAiHubInternalFeignClient.recommendMusicTaste(userId, request));
    }

    public void indexMusicRag(RagMusicIndexDTO payload) {
        invokeVoid(RESOURCE_INDEX, () -> contentAiHubInternalFeignClient.indexMusicRag(payload));
    }

    public List<String> ragVectorSearchMusic(String query) {
        AiRagSearchRequest request = new AiRagSearchRequest();
        request.setQuery(query);
        return invoke(RESOURCE_STANDARD, () -> contentAiHubInternalFeignClient.ragVectorSearchMusic(request));
    }

    public void indexArticleRag(RagArticleIndexDTO payload) {
        invokeVoid(RESOURCE_INDEX, () -> contentAiHubInternalFeignClient.indexArticleRag(payload));
    }

    public void removeArticleRag(Long articleId) {
        invokeVoid(RESOURCE_INDEX, () -> contentAiHubInternalFeignClient.removeArticleRag(articleId));
    }

    /**
     * Sentinel 只包裹跨服务边界，业务 Service 不感知规则细节。
     *
     * <p>这里**不能**写成 try-with-resources。Java 的关闭顺序是先 close 再进 catch，
     * 等到 catch 里调 Tracer 时 entry 已经 exit 了，异常记不进这个资源，
     * 于是异常比例熔断（degrade 规则里 grade=1 那几条）永远不会触发——
     * 只有按 RT 统计的慢调用熔断还活着，因为 RT 是 exit 时自动记的。
     * 必须自己持有 entry，在 exit 之前用 traceEntry 记录，exit 放进 finally。
     */
    private <T> T invoke(String resource, Supplier<T> action) {
        Entry entry = null;
        try {
            entry = SphU.entry(resource);
            return action.get();
        } catch (BlockException exception) {
            throw blockedException(exception);
        } catch (RuntimeException exception) {
            Tracer.traceEntry(exception, entry);
            if (isDownstreamTimeout(exception)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_SERVICE_TIMEOUT));
            }
            if (isDownstreamUnreachable(exception)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_SERVICE_UNAVAILABLE));
            }
            throw translateFeignFailure(exception);
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    private void invokeVoid(String resource, Runnable action) {
        Entry entry = null;
        try {
            entry = SphU.entry(resource);
            action.run();
        } catch (BlockException exception) {
            throw blockedException(exception);
        } catch (RuntimeException exception) {
            Tracer.traceEntry(exception, entry);
            if (isDownstreamTimeout(exception)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_SERVICE_TIMEOUT));
            }
            if (isDownstreamUnreachable(exception)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_SERVICE_UNAVAILABLE));
            }
            throw translateFeignFailure(exception);
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    private ApplicationException blockedException(BlockException exception) {
        ResultCode code = exception instanceof DegradeException
                ? ResultCode.FAILED_SERVICE_UNAVAILABLE
                : ResultCode.FAILED_RATE_LIMITED;
        return new ApplicationException(Result.fail(code));
    }

    // 将 forum-ai 返回的业务 Result 还原为 ApplicationException，避免前端看到 500
    private static RuntimeException translateFeignFailure(RuntimeException exception) {
        if (!(exception instanceof FeignException feignException)) {
            return exception;
        }
        if (feignException.responseBody().isPresent()) {
            try {
                byte[] body = feignException.responseBody().get().array();
                Result<?> result = OBJECT_MAPPER.readValue(body, Result.class);
                if (result != null && result.getCode() != ResultCode.SUCCESS.getCode()) {
                    throw new ApplicationException(result);
                }
            } catch (ApplicationException applicationException) {
                throw applicationException;
            } catch (Exception ignored) {
                // 解析失败时走 HTTP 状态兜底
            }
        }
        return switch (feignException.status()) {
            case 429 -> new ApplicationException(Result.fail(ResultCode.FAILED_RATE_LIMITED));
            case 502, 503 -> new ApplicationException(Result.fail(ResultCode.FAILED_SERVICE_UNAVAILABLE));
            case 504, 408 -> new ApplicationException(Result.fail(ResultCode.FAILED_SERVICE_TIMEOUT));
            default -> exception;
        };
    }

    // Feign 读超时常包装为 RetryableException，未转换时会落到全局 500
    private static boolean isDownstreamTimeout(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 12; depth++) {
            String name = current.getClass().getName();
            if (name.equals("java.net.SocketTimeoutException")
                    || name.equals("java.util.concurrent.TimeoutException")
                    || name.contains("ResourceAccessException")) {
                return true;
            }
            if (name.equals("feign.RetryableException")) {
                String message = String.valueOf(current.getMessage()).toLowerCase();
                if (message.contains("timed out") || message.contains("timeout") || message.contains("read timed")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    // Nacos 无实例或本地无法解析服务名时，Feign 会落到 UnknownHostException
    private static boolean isDownstreamUnreachable(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 12; depth++) {
            if (current instanceof java.net.UnknownHostException) {
                return true;
            }
            String message = String.valueOf(current.getMessage()).toLowerCase();
            if (message.contains("no servers available for service")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
