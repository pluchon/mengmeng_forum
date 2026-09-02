package org.pluchon.forum.service.impl.ai;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.utils.OssPendingPromoter;
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
import org.pluchon.forum.entity.vo.ai.AiPolishResponseVO;
import org.pluchon.forum.service.interfaces.ai.AiCompanionApiService;
import org.pluchon.forum.service.interfaces.ai.AiHubService;
import org.pluchon.forum.service.interfaces.ai.AiQuotaService;
import org.pluchon.forum.service.security.AiUserContext;
import org.pluchon.forum.service.security.AiUserLookupService;
import org.pluchon.forum.service.interfaces.ai.AiWorkspaceService;
import org.pluchon.forum.service.interfaces.mascot.CompanionMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.temporal.ChronoUnit;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
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
    private OssPendingPromoter ossPendingPromoter;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${forum.ai.polish-basic-hourly-limit:5}")
    private int polishBasicHourlyLimit;

    // 生图配额 consumeImageNormal 与通用生图共用，非会员额度并非 0，
    // 所以会员校验必须放在封面这个入口上，而不是去改共用的配额数字
    /**
     * AI 生成图的出图审核。
     *
     * <p>审核服务不可用时放行，与站内其它审核的取舍一致——宁可漏一张，
     * 也不该因为审核抖动就把用户已经扣过额度的图判死。
     */
    private void assertGeneratedImageClean(String storedUrl) {
        if (!StringUtils.hasText(storedUrl)) {
            return;
        }
        boolean allowed;
        try {
            allowed = aiHubService.validateImageUrl(storedUrl, null);
        } catch (Exception ex) {
            log.warn("AI 生成图审核调用失败，放行 url={}", storedUrl, ex);
            return;
        }
        if (!allowed) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED,
                    "生成的图片未通过内容审核，请换个描述再试"));
        }
    }

    private int effectiveVipTier(AiUserContext user) {
        if (user == null || !user.isVipActive() || user.getVipTier() == null) {
            return 0;
        }
        return user.getVipTier().intValue();
    }

    /**
     * 免费用户的润色限频。
     *
     * <p>润色是站内单次最贵的 AI 动作（1 个规划 + 1~4 个 worker + 1 次评审，
     * 还可能精修一轮），却是唯一没有任何短周期限制的入口——月额度可以在几分钟内被烧光。
     * 这里只按小时数**尝试次数**，失败也算：限频要防的就是反复重试。
     */
    private void assertPolishRate(AiUserContext user) {
        if (user == null || user.getId() == null) {
            return;
        }
        if (effectiveVipTier(user) >= Constant.VIP_TIER_PRO) {
            return;
        }
        String key = "ai:polish:rate:" + user.getId() + ":"
                + ZonedDateTime.now(ZoneId.of("Asia/Taipei")).truncatedTo(ChronoUnit.HOURS);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofHours(2));
        }
        if (count != null && count > polishBasicHourlyLimit) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_QUOTA_EXCEEDED,
                    "AI 润色本小时次数已用完，开通会员可解除限制"));
        }
    }

    /** 能不能用生图（进阶档同此门槛）：有效会员即可。 */
    private boolean isImageEligible(AiUserContext user) {
        return user != null && user.isVipActive() && effectiveVipTier(user) >= Constant.VIP_TIER_PRO;
    }

    private void assertCoverVip(AiUserContext user) {
        boolean proOrMax = user != null && user.isVipActive()
                && (Constant.VIP_TIER_PRO.equals(user.getVipTier())
                    || Constant.VIP_TIER_MAX.equals(user.getVipTier()));
        if (!proOrMax) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN,
                    "AI 配图为会员专享（PRO / MAX）"));
        }
    }

    // 日配额只算调用次数，不算 token。一次超长正文就能烧掉相当于几十次正常调用的钱，
    // 所以送模型之前先卡一道长度，与帖子正文的入库上限保持一致
    private void assertAiInputLength(String content) {
        if (content != null && content.length() > Constant.AI_INPUT_CONTENT_MAX_LEN) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "正文太长了，请精简到 " + Constant.AI_INPUT_CONTENT_MAX_LEN + " 字以内再试"));
        }
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
        assertAiInputLength(req.getContent());
        req.setEditorMode("markdown".equalsIgnoreCase(req.getEditorMode()) ? "markdown" : "rich");
        if (!StringUtils.hasText(req.getClientRequestId())) {
            req.setClientRequestId(UUID.randomUUID().toString());
        }
        // 档位由服务端说了算，前端传上来的一律覆盖
        req.setVipTier(effectiveVipTier(user));
        assertPolishRate(user);
        String modelCode = Constant.AI_MODEL_QWEN_FLASH;
        Long workspaceId = req.getWorkspaceId();
        if (workspaceId != null) {
            workspaceId = aiWorkspaceService.ensureWorkspace(user.getId(), workspaceId, req.getCheckpointId());
        }

        boolean reservedAdvanced = false;
        AiCallBeginResult begin = aiCallRecordService.beginCall(
                user.getId(), "ai_polish", req.getClientRequestId(), modelCode);
        rejectDuplicateAiBegin(begin);
        long startMs = System.currentTimeMillis();
        try {
            aiQuotaService.consumeAdvancedLlm(user);
            reservedAdvanced = true;
            AiHubPolishResultVO hubResult = aiHubService.polish(user.getId(), req);
            List<AiModelUsageDTO> usages = normalizeUsageItems(
                    hubResult.getUsageItems(), hubResult.getUsage(), modelCode);
            AiModelUsageDTO usage = aiPointsBillingService.aggregateUsage(usages, modelCode);
            hubResult.setUsage(usage);
            String relatedId = StringUtils.hasText(req.getClientRequestId())
                    ? req.getClientRequestId().trim() : null;
            Map<String, Object> billing = aiCallRecordService.settleSuccessBatch(
                    begin, user, "ai_polish", usages, modelCode, relatedId,
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
        // 帖子封面是会员专享。此前只有前端拦，直接打接口就能白嫖生图 —— 而生图是最贵的一档
        assertCoverVip(user);
        req.setTitle(StringUtils.hasText(req.getTitle()) ? req.getTitle().trim() : "");
        req.setContent(req.getContent().trim());
        assertAiInputLength(req.getContent());
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

        boolean reservedNormal = false;
        boolean reservedQwenFlash = false;
        AiCallBeginResult begin = aiCallRecordService.beginCall(
                user.getId(), "article_cover", req.getClientRequestId(), imageModel);
        rejectDuplicateAiBegin(begin);
        long startMs = System.currentTimeMillis();
        try {
            aiQuotaService.consumeQwenFlash(user);
            reservedQwenFlash = true;
            aiQuotaService.consumeImage(user, 1);
            reservedNormal = true;
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
                    System.currentTimeMillis() - startMs);

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
        if (!"normal".equals(q) && !"premium".equals(q)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "生图档位只能是 normal 或 premium"));
        }
        // 进阶档是会员权益，且档位由模型判定——前端传什么都要按登录态复核一遍
        if ("premium".equals(q) && !isImageEligible(user)) {
            q = "normal";
        }
        req.setQuality(q);
        boolean premium = "premium".equals(q);
        String modelCode = premium
                ? Constant.AI_MODEL_IMAGE_DASH_PREMIUM
                : Constant.AI_MODEL_IMAGE_NORMAL;
        // 进阶档单价是普通档的 2.5 倍，额度按两张扣
        int wanUnits = premium ? AiPointsBillingService.PREMIUM_WAN_UNITS : 1;

        boolean reservedNormal = false;
        boolean reservedQwenFlash = false;
        AiCallBeginResult begin = aiCallRecordService.beginCall(
                user.getId(), "companion_image", req.getClientRequestId(), modelCode);
        rejectDuplicateAiBegin(begin);
        long startMs = System.currentTimeMillis();
        try {
            aiQuotaService.consumeQwenFlash(user);
            reservedQwenFlash = true;
            aiQuotaService.consumeImage(user, wanUnits);
            reservedNormal = true;
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
            String storedPath;
            long ts = System.currentTimeMillis();
            if (req.getArticleId() != null && req.getArticleId() > 0) {
                String base = user.getId() + "_" + req.getArticleId() + "_" + ts;
                storedPath = Constant.OSS_PATH_AI_GENERATION_ARTICLE;
                storedUrl = aiFileInternalFeignClient.uploadAiGeneratedImage(
                        new AiGeneratedImageUploadRequest(user.getId(), url, storedPath, base));
            } else {
                String sid = (req.getSessionId() != null && !req.getSessionId().isBlank())
                        ? req.getSessionId().trim() : "session";
                String base = user.getId() + "_" + sid + "_" + ts;
                storedPath = Constant.OSS_PATH_AI_GENERATION_SESSION;
                storedUrl = aiFileInternalFeignClient.uploadAiGeneratedImage(
                        new AiGeneratedImageUploadRequest(user.getId(), url, storedPath, base));
            }
            // 这张图马上就要写进会话记录，等于当场绑定，所以现在就转正。
            // 留在 _pending/ 的话 7 天后会被生命周期规则收走，聊天记录里的图就没了
            storedUrl = ossPendingPromoter.promoteIfPending(storedUrl, storedPath);
            // 生成图落在 OSS 上是公开可访问的，链接能被转发出去。
            // 帖子里的图一直走 validateImageUrl，AI 生成的这条路原来是空的。
            assertGeneratedImageClean(storedUrl);
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
            releaseImageReservation(user, reservedNormal, wanUnits);
            if (reservedQwenFlash) {
                aiQuotaService.releaseQwenFlash(user);
            }
            throw ex;
        } catch (RuntimeException ex) {
            aiCallRecordService.markFailure(begin, AiCallState.FAILED, ex.getMessage());
            releaseImageReservation(user, reservedNormal, wanUnits);
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
        releaseImageReservation(user, reservedNormal, 1);
    }

    private void releaseImageReservation(AiUserContext user, boolean reservedNormal, int units) {
        if (reservedNormal) {
            aiQuotaService.releaseImage(user, units);
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
