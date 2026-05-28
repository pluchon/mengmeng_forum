package org.example.forumdemo.controller.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.ai.AiCoverHintsRequest;
import org.example.forumdemo.entity.dto.ai.AiImageRequest;
import org.example.forumdemo.entity.dto.ai.AiModelUsageDTO;
import org.example.forumdemo.entity.dto.ai.AiWriteRequest;
import org.example.forumdemo.entity.vo.ai.AiPriceEstimateVO;
import org.example.forumdemo.service.AiPointsBillingService;
import org.example.forumdemo.service.CompanionMemoryService;
import org.example.forumdemo.service.interfaces.ai.AiHubService;
import org.example.forumdemo.service.interfaces.ai.AiQuotaService;
import org.example.forumdemo.service.interfaces.file.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@Tag(name = "AI 能力", description = "写作 / 封面要点 / 生图（经配额后转发 ai-server）")
@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    private static final String K_DEEPSEEK_FLASH = "deepseek_flash";
    private static final String K_DEEPSEEK_PRO = "deepseek_pro";
    private static final String K_QWEN_FLASH = "qwen_flash";
    private static final String K_QWEN_PRO = "qwen_pro";
    private static final String K_GEMINI_PRO = "gemini_pro";
    private static final String K_CLAUDE_HAIKU = "claude_haiku";
    private static final String K_CLAUDE_SONNET = "claude_sonnet";

    @Autowired
    private AiQuotaService aiQuotaService;

    @Autowired
    private AiHubService aiHubService;

    @Autowired
    private AiPointsBillingService aiPointsBillingService;

    @Autowired
    private CompanionMemoryService companionMemoryService;

    @Autowired
    private FileService fileService;

    @Operation(summary = "预估 AI 消耗积分")
    @GetMapping("/price-estimate")
    public Result<AiPriceEstimateVO> priceEstimate(
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String route,
            @RequestParam(required = false) String quality,
            HttpServletRequest request) {
        User user = (User) request.getAttribute(Constant.USER_SESSION);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        String model;
        int in = Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS;
        int out = Constant.AI_ESTIMATE_CHAT_OUTPUT_TOKENS;
        int img = 0;
        if ("drawing".equalsIgnoreCase(skill) || "image".equalsIgnoreCase(skill)) {
            boolean premium = "premium".equalsIgnoreCase(quality);
            model = premium ? Constant.AI_MODEL_IMAGE_PREMIUM : Constant.AI_MODEL_IMAGE_NORMAL;
            in = 0;
            out = 0;
            img = 1;
        } else {
            model = aiPointsBillingService.resolveModelFromRoute(
                    route != null && !route.isBlank() ? route : "qwen-flash");
        }
        AiPriceEstimateVO vo = new AiPriceEstimateVO();
        vo.setModelCode(model);
        vo.setEstimated(true);
        vo.setPoints(aiPointsBillingService.estimatePoints(model, in, out, img));
        return Result.success(vo);
    }

    @Operation(summary = "对话写作", description = "kind: deepseek_flash | deepseek_pro | qwen_flash | qwen_pro")
    @PostMapping("/write")
    public Result<Map<String, Object>> write(@RequestBody AiWriteRequest req, HttpServletRequest request) {
        User user = (User) request.getAttribute(Constant.USER_SESSION);
        if (req == null || !StringUtils.hasText(req.getKind()) || req.getMessages() == null || req.getMessages().isEmpty()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String kind = normalizeWriteKind(req.getKind().trim().toLowerCase(Locale.ROOT));
        req.setKind(kind);
        String modelCode = modelCodeForWriteKind(kind);
        if (modelCode == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        aiPointsBillingService.ensureBalance(user,
                aiPointsBillingService.estimatePoints(modelCode,
                        Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS,
                        Constant.AI_ESTIMATE_CHAT_OUTPUT_TOKENS, 0));

        boolean reservedDeepseekFree = false;
        boolean reservedAdvanced = false;
        try {
            if (K_DEEPSEEK_FLASH.equals(kind) || K_DEEPSEEK_PRO.equals(kind)) {
                if (!aiQuotaService.hasUnlimitedDeepseek(user)) {
                    aiQuotaService.consumeDeepseekWrite(user);
                    reservedDeepseekFree = true;
                }
            } else if (K_QWEN_FLASH.equals(kind) || K_QWEN_PRO.equals(kind)
                    || K_GEMINI_PRO.equals(kind)
                    || K_CLAUDE_HAIKU.equals(kind) || K_CLAUDE_SONNET.equals(kind)) {
                aiQuotaService.consumeAdvancedLlm(user);
                reservedAdvanced = true;
            } else {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
            }
            Map<String, Object> data = aiHubService.write(user.getId(), req);
            AiModelUsageDTO usage = parseUsageFromData(data, modelCode);
            int balance = aiPointsBillingService.charge(user, "ai_write", usage, null, Constant.POINTS_SOURCE_AI_COMPANION);
            data.put("pointsCost", aiPointsBillingService.calcPoints(usage));
            data.put("balanceAfter", balance);
            return Result.success(data);
        } catch (ApplicationException ex) {
            releaseWriteReservation(user, reservedDeepseekFree, reservedAdvanced);
            throw ex;
        } catch (RuntimeException ex) {
            releaseWriteReservation(user, reservedDeepseekFree, reservedAdvanced);
            throw ex;
        }
    }

    private static String normalizeWriteKind(String kind) {
        if (kind == null) {
            return null;
        }
        return kind;
    }

    private void releaseWriteReservation(User user, boolean reservedDeepseekFree, boolean reservedAdvanced) {
        if (reservedDeepseekFree) {
            aiQuotaService.releaseDeepseekWrite(user);
        }
        if (reservedAdvanced) {
            aiQuotaService.releaseAdvancedLlm(user);
        }
    }

    @Operation(summary = "封面推荐配图要点", description = "不计入 DeepSeek 写作日额，仅审计")
    @PostMapping("/cover-hints")
    public Result<Map<String, Object>> coverHints(@RequestBody AiCoverHintsRequest req, HttpServletRequest request) {
        User user = (User) request.getAttribute(Constant.USER_SESSION);
        if (req == null || !StringUtils.hasText(req.getArticleText())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        req.setArticleText(req.getArticleText().trim());
        try {
            Map<String, Object> data = aiHubService.coverHints(user.getId(), req);
            aiQuotaService.recordCoverHint(user);
            return Result.success(data);
        } catch (ApplicationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_ENGINE));
        }
    }

    @Operation(summary = "AI 生图", description = "quality: normal | premium")
    @PostMapping("/image")
    public Result<Map<String, Object>> image(@RequestBody AiImageRequest req, HttpServletRequest request) {
        User user = (User) request.getAttribute(Constant.USER_SESSION);
        if (req == null || !StringUtils.hasText(req.getPrompt()) || !StringUtils.hasText(req.getQuality())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String q = req.getQuality().trim().toLowerCase(Locale.ROOT);
        req.setQuality(q);
        if (!"normal".equals(q) && !"premium".equals(q)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String modelCode = "premium".equals(q) ? Constant.AI_MODEL_IMAGE_PREMIUM : Constant.AI_MODEL_IMAGE_NORMAL;
        aiPointsBillingService.ensureBalance(user, aiPointsBillingService.estimatePoints(modelCode, 0, 0, 1));

        boolean reservedNormal = false;
        boolean reservedPremium = false;
        try {
            if ("normal".equals(q)) {
                aiQuotaService.consumeImageNormal(user);
                reservedNormal = true;
            } else {
                aiQuotaService.consumeImagePremium(user);
                reservedPremium = true;
            }
            Long dbSessionId = companionMemoryService.ensureSession(user.getId(), "drawing", req.getSessionId());
            Map<String, Object> data = aiHubService.image(user.getId(), req);
            AiModelUsageDTO usage = parseUsageFromData(data, modelCode);
            if (usage.getImageCount() == null || usage.getImageCount() < 1) {
                usage = aiPointsBillingService.usageForImage(modelCode, 1);
            }
            int balance = aiPointsBillingService.charge(user, "companion_image", usage, String.valueOf(dbSessionId), Constant.POINTS_SOURCE_AI_IMAGE);
            String url = data.get("url") != null ? String.valueOf(data.get("url")) : null;
            if (url == null && data.get("payload") instanceof Map<?, ?> pm) {
                Object u = pm.get("url");
                if (u != null) {
                    url = String.valueOf(u);
                }
            }
            companionMemoryService.appendTextMessage(dbSessionId, "user", req.getPrompt().trim());
            if (url == null || url.isBlank()) {
                log.warn("AI 生图返回空 URL userId={} quality={} dataKeys={}", user.getId(), q, data.keySet());
                throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_ENGINE,
                        "AI 未返回图片地址，请检查进阶生图密钥(HUANAPI_IMAGE_KEY)或改用普通档"));
            }
            String storedUrl = fileService.uploadCompanionAiImageFromRemote(user.getId(), url);
            data.put("url", storedUrl);
            companionMemoryService.appendImageMessage(dbSessionId, "assistant", storedUrl, null);
            data.put("sessionId", String.valueOf(dbSessionId));
            data.put("pointsCost", aiPointsBillingService.calcPoints(usage));
            data.put("balanceAfter", balance);
            data.put("modelCode", modelCode);
            return Result.success(data);
        } catch (ApplicationException ex) {
            if (reservedNormal) {
                aiQuotaService.releaseImageNormal(user);
            }
            if (reservedPremium) {
                aiQuotaService.releaseImagePremium(user);
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (reservedNormal) {
                aiQuotaService.releaseImageNormal(user);
            }
            if (reservedPremium) {
                aiQuotaService.releaseImagePremium(user);
            }
            log.error("AI 生图失败 userId={} quality={}: {}", user.getId(), q, ex.getMessage(), ex);
            String detail = ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getMessage() : "请稍后重试";
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_ENGINE, "AI 生图失败: " + detail));
        }
    }

    @SuppressWarnings("unchecked")
    private AiModelUsageDTO parseUsageFromData(Map<String, Object> data, String fallbackModel) {
        if (data == null) {
            return aiPointsBillingService.normalizeUsage(null, fallbackModel);
        }
        Object u = data.get("usage");
        AiModelUsageDTO dto = new AiModelUsageDTO();
        if (u instanceof Map<?, ?> um) {
            Object mc = um.get("model_code");
            if (mc == null) {
                mc = um.get("model");
            }
            if (mc != null) {
                dto.setModelCode(String.valueOf(mc));
            }
            dto.setInputTokens(toInt(um.get("input_tokens")));
            dto.setOutputTokens(toInt(um.get("output_tokens")));
            dto.setImageCount(toInt(um.get("images")));
            if (dto.getImageCount() == 0) {
                dto.setImageCount(toInt(um.get("image_count")));
            }
            Object est = um.get("estimated");
            dto.setEstimated(est instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(est)));
        }
        return aiPointsBillingService.normalizeUsage(dto, fallbackModel);
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String modelCodeForWriteKind(String kind) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case K_DEEPSEEK_FLASH -> "deepseek-v4-flash";
            case K_DEEPSEEK_PRO -> "deepseek-v4-pro";
            case K_QWEN_FLASH -> "qwen3.6-flash";
            case K_QWEN_PRO -> Constant.AI_MODEL_QWEN_DEEP;
            case K_GEMINI_PRO -> Constant.AI_MODEL_GEMINI_DEEP;
            case K_CLAUDE_HAIKU -> Constant.AI_MODEL_CLAUDE_HAIKU;
            case K_CLAUDE_SONNET -> Constant.AI_MODEL_CLAUDE_SONNET;
            default -> null;
        };
    }
}
