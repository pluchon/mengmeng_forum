package org.pluchon.forum.service.impl.ai;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.AiCallState;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.converter.AiHubConverter;
import org.pluchon.forum.api.content.AiGeneratedImageUploadRequest;
import org.pluchon.forum.cloud.feign.AiFileInternalFeignClient;
import org.pluchon.forum.entity.dto.AiArticleCoverRequest;
import org.pluchon.forum.entity.dto.AiCoverHintsRequest;
import org.pluchon.forum.entity.dto.AiImageRequest;
import org.pluchon.forum.entity.dto.AiModelUsageDTO;
import org.pluchon.forum.entity.dto.AiPolishRequest;
import org.pluchon.forum.entity.vo.ai.AiArticleCoverResponseVO;
import org.pluchon.forum.entity.vo.ai.AiHubCoverHintsResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubArticleCoverResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubImageResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubPolishResultVO;
import org.pluchon.forum.entity.vo.ai.AiCallBeginResult;
import org.pluchon.forum.entity.vo.ai.AiImageResponseVO;
import org.pluchon.forum.entity.vo.ai.AiPriceEstimateVO;
import org.pluchon.forum.entity.vo.ai.AiPolishResponseVO;
import org.pluchon.forum.service.interfaces.ai.AiCompanionApiService;
import org.pluchon.forum.service.interfaces.ai.AiHubService;
import org.pluchon.forum.service.interfaces.ai.AiQuotaService;
import org.pluchon.forum.service.security.AiUserContext;
import org.pluchon.forum.service.security.AiUserLookupService;
import org.pluchon.forum.service.interfaces.ai.AiWorkspaceService;
import org.pluchon.forum.service.interfaces.mascot.CompanionMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// AI 润色/生图用例：配额预占、Hub 调用、计费与 OSS 落库
@Slf4j
@Service
public class AiCompanionApiServiceImpl implements AiCompanionApiService {

    @Autowired
    private AiUserLookupService aiUserLookupService;

    @Autowired
    private AiQuotaService aiQuotaService;

    @Autowired
    private AiHubService aiHubService;

    @Autowired
    private AiPointsBillingService aiPointsBillingService;

    @Autowired
    private AiCallRecordService aiCallRecordService;

    @Autowired
    private CompanionMemoryService companionMemoryService;

    @Autowired
    private AiFileInternalFeignClient aiFileInternalFeignClient;

    @Autowired
    private AiWorkspaceService aiWorkspaceService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public AiPriceEstimateVO priceEstimate(Long userId, String skill, String route, String quality) {
        requireUser(userId);
        String model;
        int in = Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS;
        int out = Constant.AI_ESTIMATE_CHAT_OUTPUT_TOKENS;
        int img = 0;
        if ("drawing".equalsIgnoreCase(skill) || "image".equalsIgnoreCase(skill)) {
            if ("premium".equalsIgnoreCase(quality)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "生图仅支持 normal 档"));
            }
            model = Constant.AI_MODEL_IMAGE_NORMAL;
            in = 0;
            out = 0;
            img = 1;
        } else {
            requireUser(userId);
            model = Constant.AI_MODEL_QWEN_DEEP;
        }
        AiPriceEstimateVO vo = new AiPriceEstimateVO();
        vo.setModelCode(model);
        vo.setEstimated(true);
        vo.setPoints(aiPointsBillingService.estimatePoints(model, in, out, img));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPolishResponseVO polish(Long userId, AiPolishRequest req) {
        AiUserContext user = requireUser(userId);
        if (req == null || !StringUtils.hasText(req.getContent())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        req.setTitle(StringUtils.hasText(req.getTitle()) ? req.getTitle().trim() : "");
        req.setContent(req.getContent().trim());
        req.setEditorMode("markdown".equalsIgnoreCase(req.getEditorMode()) ? "markdown" : "rich");
        if (!StringUtils.hasText(req.getClientRequestId())) {
            req.setClientRequestId(UUID.randomUUID().toString());
        }
        String modelCode = Constant.AI_MODEL_QWEN_FLASH;
        boolean usePoints = Boolean.TRUE.equals(req.getUsePointsBilling());
        Long workspaceId = req.getWorkspaceId();
        if (workspaceId != null) {
            workspaceId = aiWorkspaceService.ensureWorkspace(user.getId(), workspaceId, req.getCheckpointId());
        }
        if (usePoints) {
            aiPointsBillingService.ensureBalance(user,
                    aiPointsBillingService.estimatePoints(modelCode,
                            Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS,
                            Constant.AI_ESTIMATE_CHAT_OUTPUT_TOKENS, 0));
        }

        boolean reservedAdvanced = false;
        AiCallBeginResult begin = aiCallRecordService.beginCall(
                user.getId(), "ai_polish", req.getClientRequestId(), modelCode);
        rejectDuplicateAiBegin(begin);
        long startMs = System.currentTimeMillis();
        try {
            if (!usePoints) {
                aiQuotaService.consumeAdvancedLlm(user);
                reservedAdvanced = true;
            }
            AiHubPolishResultVO hubResult = aiHubService.polish(user.getId(), req);
            List<AiModelUsageDTO> usages = normalizeUsageItems(
                    hubResult.getUsageItems(), hubResult.getUsage(), modelCode);
            AiModelUsageDTO usage = aiPointsBillingService.aggregateUsage(usages, modelCode);
            hubResult.setUsage(usage);
            String relatedId = StringUtils.hasText(req.getClientRequestId())
                    ? req.getClientRequestId().trim() : null;
            Map<String, Object> billing = aiCallRecordService.settleSuccessBatch(
                    begin, user, "ai_polish", usages, modelCode, relatedId,
                    Constant.POINTS_SOURCE_AI_COMPANION, usePoints,
                    System.currentTimeMillis() - startMs);
            AiPolishResponseVO response = AiHubConverter.toPolishResponse(hubResult, billing);
            if (workspaceId == null) {
                workspaceId = aiWorkspaceService.ensureWorkspace(user.getId(), null, req.getCheckpointId());
            }
            Long versionId = aiWorkspaceService.appendGeneratedArtifact(user.getId(), workspaceId,
                    req.getParentVersionId(), "POLISH", artifactJson("POLISH", response), req.getCheckpointId());
            response.setWorkspaceId(workspaceId);
            response.setWorkspaceVersionId(versionId);
            return response;
        } catch (RuntimeException ex) {
            aiCallRecordService.markFailure(begin, AiCallState.FAILED, ex.getMessage());
            releasePolishReservation(user, reservedAdvanced);
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiArticleCoverResponseVO articleCover(Long userId, AiArticleCoverRequest req) {
        AiUserContext user = requireUser(userId);
        if (req == null || !StringUtils.hasText(req.getContent()) || !StringUtils.hasText(req.getQuality())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        req.setTitle(StringUtils.hasText(req.getTitle()) ? req.getTitle().trim() : "");
        req.setContent(req.getContent().trim());
        req.setEditorMode("markdown".equalsIgnoreCase(req.getEditorMode()) ? "markdown" : "rich");
        req.setUserPrompt(StringUtils.hasText(req.getUserPrompt()) ? req.getUserPrompt().trim() : "");
        String quality = req.getQuality().trim().toLowerCase(Locale.ROOT);
        if (!"normal".equals(quality)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "生图仅支持 normal 档"));
        }
        req.setQuality(quality);
        if (!StringUtils.hasText(req.getClientRequestId())) {
            req.setClientRequestId(UUID.randomUUID().toString());
        }
        String imageModel = Constant.AI_MODEL_IMAGE_NORMAL;
        boolean usePoints = Boolean.TRUE.equals(req.getUsePointsBilling());
        if (usePoints) {
            aiPointsBillingService.ensureBalance(
                    user, aiPointsBillingService.estimatePoints(imageModel, 0, 0, 1));
        }

        boolean reservedNormal = false;
        boolean reservedQwenFlash = false;
        AiCallBeginResult begin = aiCallRecordService.beginCall(
                user.getId(), "article_cover", req.getClientRequestId(), imageModel);
        rejectDuplicateAiBegin(begin);
        long startMs = System.currentTimeMillis();
        try {
            if (!usePoints) {
                aiQuotaService.consumeQwenFlash(user);
                reservedQwenFlash = true;
                aiQuotaService.consumeImageNormal(user);
                reservedNormal = true;
            }
            AiHubArticleCoverResultVO hubResult = aiHubService.articleCover(user.getId(), req);
            if (!StringUtils.hasText(hubResult.getUrl())) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_ENGINE, "AI 未返回封面图片"));
            }
            List<AiModelUsageDTO> usages = normalizeUsageItems(
                    hubResult.getUsageItems(), hubResult.getUsage(), imageModel);
            AiModelUsageDTO aggregate = aiPointsBillingService.aggregateUsage(usages, imageModel);
            hubResult.setUsage(aggregate);
            Map<String, Object> billing = aiCallRecordService.settleSuccessBatch(
                    begin, user, "article_cover", usages, imageModel, req.getClientRequestId(),
                    Constant.POINTS_SOURCE_AI_IMAGE, usePoints, System.currentTimeMillis() - startMs);

            String base = user.getId() + "_article_cover_" + System.currentTimeMillis();
            String storedUrl = aiFileInternalFeignClient.uploadAiGeneratedImage(
                    new AiGeneratedImageUploadRequest(
                            user.getId(), hubResult.getUrl(), Constant.OSS_PATH_AI_GENERATION_ARTICLE, base));
            AiArticleCoverResponseVO response = AiHubConverter.toArticleCoverResponse(hubResult, storedUrl, billing);
            aiQuotaService.recordCoverHint(user);
            if (req.getWorkspaceId() != null) {
                Long workspaceId = aiWorkspaceService.ensureWorkspace(
                        user.getId(), req.getWorkspaceId(), req.getCheckpointId());
                Long versionId = aiWorkspaceService.appendGeneratedArtifact(
                        user.getId(), workspaceId, req.getParentVersionId(),
                        "ARTICLE_COVER", artifactJson("ARTICLE_COVER", response), req.getCheckpointId());
                response.setWorkspaceId(workspaceId);
                response.setWorkspaceVersionId(versionId);
            }
            return response;
        } catch (ApplicationException ex) {
            aiCallRecordService.markFailure(begin, AiCallState.FAILED, ex.getMessage());
            releaseImageReservation(user, reservedNormal);
            if (reservedQwenFlash) {
                aiQuotaService.releaseQwenFlash(user);
            }
            throw ex;
        } catch (RuntimeException ex) {
            aiCallRecordService.markFailure(begin, AiCallState.FAILED, ex.getMessage());
            releaseImageReservation(user, reservedNormal);
            if (reservedQwenFlash) {
                aiQuotaService.releaseQwenFlash(user);
            }
            log.error("AI 文章封面生成失败 userId={} quality={}: {}", user.getId(), quality, ex.getMessage(), ex);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_ENGINE, "AI 封面生成失败，请稍后重试"));
        }
    }

    @Override
    public AiHubCoverHintsResultVO coverHints(Long userId, AiCoverHintsRequest req) {
        AiUserContext user = requireUser(userId);
        if (req == null || !StringUtils.hasText(req.getArticleText())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        req.setArticleText(req.getArticleText().trim());
        try {
            AiHubCoverHintsResultVO result = aiHubService.coverHints(user.getId(), req);
            aiQuotaService.recordCoverHint(user);
            if (req.getWorkspaceId() != null) {
                Long workspaceId = aiWorkspaceService.ensureWorkspace(user.getId(), req.getWorkspaceId(), req.getCheckpointId());
                Long versionId = aiWorkspaceService.appendGeneratedArtifact(user.getId(), workspaceId,
                        req.getParentVersionId(), "COVER_HINTS", artifactJson("COVER_HINTS", result), req.getCheckpointId());
                result.setWorkspaceId(workspaceId);
                result.setWorkspaceVersionId(versionId);
            }
            return result;
        } catch (ApplicationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_ENGINE));
        }
    }

    @Override
    public AiImageResponseVO image(Long userId, AiImageRequest req) {
        AiUserContext user = requireUser(userId);
        if (req == null || !StringUtils.hasText(req.getPrompt()) || !StringUtils.hasText(req.getQuality())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String q = req.getQuality().trim().toLowerCase(Locale.ROOT);
        req.setQuality(q);
        if (!"normal".equals(q)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "生图仅支持 normal 档"));
        }
        String modelCode = Constant.AI_MODEL_IMAGE_NORMAL;
        boolean usePoints = Boolean.TRUE.equals(req.getUsePointsBilling());
        if (usePoints) {
            aiPointsBillingService.ensureBalance(user, aiPointsBillingService.estimatePoints(modelCode, 0, 0, 1));
        }

        boolean reservedNormal = false;
        boolean reservedQwenFlash = false;
        AiCallBeginResult begin = aiCallRecordService.beginCall(
                user.getId(), "companion_image", req.getClientRequestId(), modelCode);
        rejectDuplicateAiBegin(begin);
        long startMs = System.currentTimeMillis();
        try {
            if (!usePoints) {
                aiQuotaService.consumeQwenFlash(user);
                reservedQwenFlash = true;
                aiQuotaService.consumeImageNormal(user);
                reservedNormal = true;
            }
            boolean ephemeral = Boolean.TRUE.equals(req.getEphemeral());
            Long dbSessionId = null;
            if (!ephemeral) {
                dbSessionId = companionMemoryService.ensureSession(user.getId(), "drawing", req.getSessionId());
            }
            AiHubImageResultVO hubResult = aiHubService.image(user.getId(), req);
            AiModelUsageDTO usage = aiPointsBillingService.normalizeUsage(hubResult.getUsage(), modelCode);
            if (usage.getImageCount() == null || usage.getImageCount() < 1) {
                usage = aiPointsBillingService.usageForImage(modelCode, 1);
            }
            String chargeRef = StringUtils.hasText(req.getClientRequestId())
                    ? req.getClientRequestId().trim()
                    : (ephemeral
                    ? (req.getSessionId() != null ? req.getSessionId() : "ephemeral-image")
                    : String.valueOf(dbSessionId));
            Map<String, Object> billing = aiCallRecordService.settleSuccess(
                    begin, user, "companion_image", usage, chargeRef,
                    Constant.POINTS_SOURCE_AI_IMAGE, usePoints,
                    System.currentTimeMillis() - startMs);
            String url = hubResult.getUrl();
            if (!ephemeral && dbSessionId != null) {
                companionMemoryService.appendTextMessage(dbSessionId, "user", req.getPrompt().trim());
            }
            if (url == null || url.isBlank()) {
                log.warn("AI 生图返回空 URL userId={} quality={}", user.getId(), q);
                throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_ENGINE,
                        "AI 未返回图片地址，请稍后重试"));
            }
            String storedUrl;
            long ts = System.currentTimeMillis();
            if (req.getArticleId() != null && req.getArticleId() > 0) {
                String base = user.getId() + "_" + req.getArticleId() + "_" + ts;
                storedUrl = aiFileInternalFeignClient.uploadAiGeneratedImage(
                        new AiGeneratedImageUploadRequest(user.getId(), url, Constant.OSS_PATH_AI_GENERATION_ARTICLE, base));
            } else {
                String sid = (req.getSessionId() != null && !req.getSessionId().isBlank())
                        ? req.getSessionId().trim() : "session";
                String base = user.getId() + "_" + sid + "_" + ts;
                storedUrl = aiFileInternalFeignClient.uploadAiGeneratedImage(
                        new AiGeneratedImageUploadRequest(user.getId(), url, Constant.OSS_PATH_AI_GENERATION_SESSION, base));
            }
            if (!ephemeral && dbSessionId != null) {
                companionMemoryService.appendImageMessage(dbSessionId, "assistant", storedUrl, null);
            }
            AiImageResponseVO response = AiHubConverter.toImageResponse(hubResult, modelCode, chargeRef, storedUrl, billing);
            if (req.getWorkspaceId() != null) {
                Long workspaceId = aiWorkspaceService.ensureWorkspace(user.getId(), req.getWorkspaceId(), req.getCheckpointId());
                Long versionId = aiWorkspaceService.appendGeneratedArtifact(user.getId(), workspaceId,
                        req.getParentVersionId(), "COVER_IMAGE", artifactJson("COVER_IMAGE", response), req.getCheckpointId());
                response.setWorkspaceId(workspaceId);
                response.setWorkspaceVersionId(versionId);
            }
            return response;
        } catch (ApplicationException ex) {
            aiCallRecordService.markFailure(begin, AiCallState.FAILED, ex.getMessage());
            releaseImageReservation(user, reservedNormal);
            if (reservedQwenFlash) {
                aiQuotaService.releaseQwenFlash(user);
            }
            throw ex;
        } catch (RuntimeException ex) {
            aiCallRecordService.markFailure(begin, AiCallState.FAILED, ex.getMessage());
            releaseImageReservation(user, reservedNormal);
            if (reservedQwenFlash) {
                aiQuotaService.releaseQwenFlash(user);
            }
            log.error("AI 生图失败 userId={} quality={}: {}", user.getId(), q, ex.getMessage(), ex);
            String detail = ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getMessage() : "请稍后重试";
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_ENGINE, "AI 生图失败: " + detail));
        }
    }

    private AiUserContext requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        AiUserContext user = aiUserLookupService.getById(userId);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return user;
    }

    private void releasePolishReservation(AiUserContext user, boolean reservedAdvanced) {
        if (reservedAdvanced) {
            aiQuotaService.releaseAdvancedLlm(user);
        }
    }

    private void releaseImageReservation(AiUserContext user, boolean reservedNormal) {
        if (reservedNormal) {
            aiQuotaService.releaseImageNormal(user);
        }
    }

    private void rejectDuplicateAiBegin(AiCallBeginResult begin) {
        if (begin == null) {
            return;
        }
        if (begin.isDuplicateSuccess()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "该 AI 请求已处理，请勿重复提交"));
        }
        if (begin.isTerminalFailure()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "该 AI 请求已失败，请更换 clientRequestId 后重试"));
        }
    }

    private List<AiModelUsageDTO> normalizeUsageItems(
            List<AiModelUsageDTO> usageItems,
            AiModelUsageDTO aggregate,
            String fallbackModel) {
        List<AiModelUsageDTO> normalized = new ArrayList<>();
        if (usageItems != null) {
            for (AiModelUsageDTO item : usageItems) {
                normalized.add(aiPointsBillingService.normalizeUsage(item, fallbackModel));
            }
        }
        if (normalized.isEmpty()) {
            normalized.add(aiPointsBillingService.normalizeUsage(aggregate, fallbackModel));
        }
        return normalized;
    }

    private String artifactJson(String artifactType, Object payload) {
        return objectMapper.createObjectNode()
                .put("artifactType", artifactType)
                .set("payload", objectMapper.valueToTree(payload))
                .toString();
    }
}
