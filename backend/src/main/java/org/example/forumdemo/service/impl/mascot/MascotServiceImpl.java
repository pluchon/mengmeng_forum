package org.example.forumdemo.service.impl.mascot;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.AiCallState;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.ForumMascotModel;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.ai.AiModelUsageDTO;
import org.example.forumdemo.entity.dto.ai.AiImageRequest;
import org.example.forumdemo.entity.dto.mascot.MascotChatRequest;
import org.example.forumdemo.entity.dto.mascot.MascotHistoryTurn;
import org.example.forumdemo.converter.MascotConverter;
import org.example.forumdemo.entity.vo.ai.AiCallBeginResult;
import org.example.forumdemo.entity.vo.ai.AiImageResponseVO;
import org.example.forumdemo.entity.vo.mascot.MascotChatResponseVO;
import org.example.forumdemo.entity.vo.mascot.MascotModelPublicVO;
import org.example.forumdemo.mapper.ForumMascotModelMapper;
import org.example.forumdemo.service.impl.ai.AiCallRecordService;
import org.example.forumdemo.service.impl.ai.AiPointsBillingService;
import org.example.forumdemo.service.interfaces.mascot.CompanionMemoryService;
import org.example.forumdemo.service.interfaces.ai.AiQuotaService;
import org.example.forumdemo.service.interfaces.ai.AiCompanionApiService;
import org.example.forumdemo.service.interfaces.mascot.MascotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class MascotServiceImpl implements MascotService {

    @Value("${forum.mascot.ai-url}")
    private String mascotAiUrl;

    @Value("${forum.mascot.internal-key:}")
    private String internalKey;

    @Value("${forum.mascot.basic-daily-limit:30}")
    private int basicDailyLimit;

    @Value("${forum.mascot.treat-admin-as-vip:true}")
    private boolean treatAdminAsVip;

    @Autowired
    private RestTemplate forumRestTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AiPointsBillingService aiPointsBillingService;

    @Resource
    private AiCallRecordService aiCallRecordService;

    @Resource
    private AiQuotaService aiQuotaService;

    @Resource
    private AiCompanionApiService aiCompanionApiService;

    @Resource
    private CompanionMemoryService companionMemoryService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ForumMascotModelMapper forumMascotModelMapper;

    @Resource
    private MascotArticleRagHelper mascotArticleRagHelper;

    @Override
    public List<MascotModelPublicVO> listPublicModels() {
        List<ForumMascotModel> list = forumMascotModelMapper.selectList(
                Wrappers.lambdaQuery(ForumMascotModel.class)
                        .eq(ForumMascotModel::getDeleteState, (byte) 0)
                        .eq(ForumMascotModel::getShelfStatus, (byte) 1)
                        .orderByAsc(ForumMascotModel::getSortOrder)
                        .orderByDesc(ForumMascotModel::getId));
        return list.stream().map(m -> {
            MascotModelPublicVO v = new MascotModelPublicVO();
            v.setId(m.getId());
            v.setCode(m.getCode());
            v.setName(m.getName());
            v.setModelRelPath(m.getModelRelPath());
            v.setModelScale(m.getModelScale());
            v.setPosX(m.getPosX());
            v.setPosY(m.getPosY());
            v.setStageWidth(m.getStageWidth());
            v.setStageHeight(m.getStageHeight());
            return v;
        }).toList();
    }

    private boolean isVip(User user) {
        Byte tier = user.getVipTier();
        if (tier != null && tier > 0) {
            Date exp = user.getVipExpireAt();
            if (exp == null || exp.after(new Date())) {
                return true;
            }
        }
        if (treatAdminAsVip) {
            return user.getIsAdmin() != null && user.getIsAdmin() == 1;
        }
        return false;
    }

    private String quotaKey(Long userId) {
        String day = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
        return Constant.REDIS_KEY_MASCOT_DAILY_CHAT + day + ":" + userId;
    }

    private void reserveBasicSlot(Long userId) {
        String key = quotaKey(userId);
        Long c = stringRedisTemplate.opsForValue().increment(key);
        if (c != null && c == 1L) {
            stringRedisTemplate.expire(key, Duration.ofHours(50));
        }
        if (c != null && c > basicDailyLimit) {
            stringRedisTemplate.opsForValue().decrement(key);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_QUOTA));
        }
    }

    private void releaseBasicSlot(Long userId) {
        try {
            stringRedisTemplate.opsForValue().decrement(quotaKey(userId));
        } catch (Exception e) {
            log.warn("看板娘额度回滚失败 userId={}", userId, e);
        }
    }

    private String normalizeSkill(MascotChatRequest request) {
        return companionMemoryService.normalizeSkill(request.getSkill());
    }

    private String normalizeLlmRoute(String route) {
        return "qwen-deep".equals(route) ? "qwen-deep" : "qwen-flash";
    }

    private int effectiveVipTier(User user) {
        Byte tier = user.getVipTier();
        int t = tier != null ? tier.intValue() : 0;
        if (treatAdminAsVip && user.getIsAdmin() != null && user.getIsAdmin() == 1) {
            return Math.max(t, Constant.VIP_TIER_MAX.intValue());
        }
        if (!isVip(user)) {
            return 0;
        }
        return Math.max(t, Constant.VIP_TIER_PRO.intValue());
    }

    private String resolveLlmRoute(MascotChatRequest request, boolean vip, String skill, User user) {
        if ("help".equals(skill)) {
            return "qwen-flash";
        }
        int tier = effectiveVipTier(user);
        return vip && tier >= Constant.VIP_TIER_PRO ? "qwen-deep" : "qwen-flash";
    }

    private String featureCode(String skill) {
        return switch (skill) {
            case "help" -> "companion_help";
            case "drawing" -> "companion_image";
            case "chat" -> "companion_chat";
            default -> "companion_writing";
        };
    }

    private void reserveAiQuota(User user, String route, boolean[] reservedQwenFlash, boolean[] reservedAdvanced) {
        if (route.startsWith("qwen-deep")) {
            aiQuotaService.consumeAdvancedLlm(user);
            reservedAdvanced[0] = true;
        } else {
            aiQuotaService.consumeQwenFlash(user);
            reservedQwenFlash[0] = true;
        }
    }

    private void releaseAiQuota(User user, boolean reservedQwenFlash, boolean reservedAdvanced) {
        if (reservedQwenFlash) {
            aiQuotaService.releaseQwenFlash(user);
        }
        if (reservedAdvanced) {
            aiQuotaService.releaseAdvancedLlm(user);
        }
    }

    private String normalizeAppearanceForPy(MascotChatRequest request) {
        if (request.getMascotModelCode() != null && !request.getMascotModelCode().isBlank()) {
            return request.getMascotModelCode().trim();
        }
        if (request.getAppearance() != null && !request.getAppearance().isBlank()) {
            return request.getAppearance().trim();
        }
        return "snow_miku";
    }

    private List<Map<String, String>> toPyHistory(List<MascotHistoryTurn> history) {
        List<Map<String, String>> list = new ArrayList<>();
        if (history == null) {
            return list;
        }
        for (MascotHistoryTurn t : history) {
            if (t == null || t.getRole() == null || t.getContent() == null) {
                continue;
            }
            String role = t.getRole().trim().toLowerCase(Locale.ROOT);
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            String content = t.getContent().trim();
            if (content.isEmpty()) {
                continue;
            }
            Map<String, String> m = new HashMap<>(2);
            m.put("role", role);
            m.put("content", content.length() > 2000 ? content.substring(0, 2000) : content);
            list.add(m);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private AiModelUsageDTO parseUsage(Map<String, Object> body, String fallbackModel) {
        Object u = body.get("usage");
        AiModelUsageDTO dto = new AiModelUsageDTO();
        if (u instanceof Map<?, ?> um) {
            Object mc = um.get("model_code");
            if (mc == null) {
                mc = um.get("model");
            }
            if (mc != null) {
                dto.setModelCode(String.valueOf(mc));
            }
            dto.setInputTokens(intVal(um.get("input_tokens")));
            dto.setOutputTokens(intVal(um.get("output_tokens")));
            dto.setImageCount(intVal(um.get("images")));
            if (dto.getImageCount() == null) {
                dto.setImageCount(intVal(um.get("image_count")));
            }
            Object est = um.get("estimated");
            dto.setEstimated(est instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(est)));
            aiPointsBillingService.applyLatencyFromMap(dto, um);
        }
        return aiPointsBillingService.normalizeUsage(dto, fallbackModel);
    }

    private Map<String, Object> billMascotUsage(AiCallBeginResult begin, User user, String skill,
                                                AiModelUsageDTO usage, String relatedId,
                                                boolean usePointsBilling, long latencyMs) {
        return aiCallRecordService.settleSuccess(
                begin,
                user,
                featureCode(skill),
                usage,
                relatedId,
                Constant.POINTS_SOURCE_AI_COMPANION,
                usePointsBilling,
                latencyMs);
    }

    private String billingRelatedId(MascotChatRequest request, String fallback) {
        if (request.getClientRequestId() != null && !request.getClientRequestId().isBlank()) {
            return request.getClientRequestId().trim();
        }
        return fallback;
    }

    private void rejectDuplicateMascotBegin(AiCallBeginResult begin) {
        if (begin == null) {
            return;
        }
        if (begin.isDuplicateSuccess()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "该对话请求已处理，请勿重复提交"));
        }
        if (begin.isTerminalFailure()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "该对话请求已失败，请更换 clientRequestId"));
        }
    }

    private void reserveUsageQuota(User user, String skill, String route,
                                 boolean[] reservedQwenFlash, boolean[] reservedAdvanced) {
        if ("writing".equals(skill) || "chat".equals(skill) || "help".equals(skill)) {
            reserveAiQuota(user, route, reservedQwenFlash, reservedAdvanced);
        }
    }

    private AiImageResponseVO delegateMascotImage(
            User user,
            MascotChatRequest request,
            String imagePrompt,
            String sessionKey,
            Long dbSessionId) {
        if (!isVip(user)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_QUOTA, "生图功能仅向会员开放"));
        }
        AiImageRequest imageRequest = new AiImageRequest();
        imageRequest.setPrompt(imagePrompt);
        imageRequest.setQuality(resolveImageQuality(request, user));
        imageRequest.setSessionId(sessionKey);
        imageRequest.setEphemeral(true);
        imageRequest.setUsePointsBilling(Boolean.TRUE.equals(request.getUsePointsBilling()));
        imageRequest.setClientRequestId(imageRequestId(request, sessionKey));
        AiImageResponseVO image = aiCompanionApiService.image(user.getId(), imageRequest);
        if (dbSessionId != null && image.getUrl() != null && !image.getUrl().isBlank()) {
            companionMemoryService.appendImageMessage(dbSessionId, "assistant", image.getUrl(), imagePrompt);
        }
        return image;
    }

    private String resolveImageQuality(MascotChatRequest request, User user) {
        if ("premium".equalsIgnoreCase(request.getImageQuality()) && isVip(user)) {
            return "premium";
        }
        return "normal";
    }

    private String imageRequestId(MascotChatRequest request, String sessionKey) {
        if (request.getClientRequestId() != null && !request.getClientRequestId().isBlank()) {
            return request.getClientRequestId().trim() + ":image";
        }
        return "mascot-image-" + sessionKey + "-" + System.currentTimeMillis();
    }

    private boolean isImageAction(Map<String, Object> moduleData) {
        return "IMAGE".equalsIgnoreCase(String.valueOf(moduleData.get("action")));
    }

    private static Integer intVal(Object o) {
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

    @Override
    @SuppressWarnings("rawtypes")
    public MascotChatResponseVO chat(User user, MascotChatRequest request) {
        String skill = normalizeSkill(request);
        boolean ephemeral = Boolean.TRUE.equals(request.getEphemeral());

        boolean vip = isVip(user);
        boolean usePoints = Boolean.TRUE.equals(request.getUsePointsBilling());
        boolean reservedBasic = false;
        String route = normalizeLlmRoute(resolveLlmRoute(request, vip, skill, user));
        String fallbackModel = aiPointsBillingService.resolveModelFromRoute(route);

        if (usePoints) {
            aiPointsBillingService.ensureBalance(user,
                    aiPointsBillingService.estimatePoints(fallbackModel,
                            Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS,
                            Constant.AI_ESTIMATE_CHAT_OUTPUT_TOKENS, 0));
        } else if (!vip) {
            reserveBasicSlot(user.getId());
            reservedBasic = true;
        }

        boolean[] reservedQwenFlash = {false};
        boolean[] reservedAdvanced = {false};
        if (!usePoints) {
            try {
                reserveUsageQuota(user, skill, route, reservedQwenFlash, reservedAdvanced);
            } catch (ApplicationException ex) {
                if (reservedBasic) {
                    releaseBasicSlot(user.getId());
                }
                throw ex;
            }
        }

        Long dbSessionId = null;
        List<MascotHistoryTurn> mergedHistory;
        if (ephemeral) {
            mergedHistory = request.getHistory() != null ? request.getHistory() : List.of();
        } else {
            dbSessionId = companionMemoryService.ensureSession(user.getId(), skill, request.getSessionId());
            List<MascotHistoryTurn> dbHistory = companionMemoryService.loadHistoryTurns(dbSessionId, 16);
            mergedHistory = dbHistory.isEmpty() ? request.getHistory() : dbHistory;
        }

        String pySessionKey = ephemeral
                ? (request.getSessionId() != null && !request.getSessionId().isBlank()
                ? request.getSessionId().trim() : String.valueOf(user.getId()))
                : String.valueOf(dbSessionId);
        String billingRelatedId = billingRelatedId(request, pySessionKey);
        AiCallBeginResult begin = aiCallRecordService.beginCall(
                user.getId(), featureCode(skill), request.getClientRequestId(), fallbackModel);
        rejectDuplicateMascotBegin(begin);
        long startMs = System.currentTimeMillis();

        Map<String, Object> pyBody = new HashMap<>();
        pyBody.put("message", request.getMessage().trim());
        pyBody.put("session_id", pySessionKey);
        pyBody.put("appearance", normalizeAppearanceForPy(request));
        pyBody.put("tier", vip ? "vip" : "basic");
        int vipTier = user.getVipTier() != null ? user.getVipTier().intValue() : 0;
        if (vip && vipTier <= 0) {
            vipTier = 1;
        }
        pyBody.put("vip_tier", vipTier);
        pyBody.put("skill", skill);
        pyBody.put("history", toPyHistory(mergedHistory));
        pyBody.put("llm_provider", route);
        if (request.getClientDatetime() != null && !request.getClientDatetime().isBlank()) {
            pyBody.put("client_datetime", request.getClientDatetime().trim());
        }

        RestTemplate restTemplate = forumRestTemplate;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalKey != null && !internalKey.isBlank()) {
            headers.set("X-Internal-Key", internalKey);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(gatewayRequest(pyBody), headers);

        if (!ephemeral && dbSessionId != null) {
            companionMemoryService.appendTextMessage(dbSessionId, "user", request.getMessage().trim());
        }

        Map body;
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(mascotAiUrl, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("bad status");
            }
            body = response.getBody();
        } catch (ApplicationException ex) {
            aiCallRecordService.markFailure(begin, AiCallState.FAILED, ex.getMessage());
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            throw ex;
        } catch (Exception e) {
            log.warn("看板娘 Python 调用失败: {}", e.getMessage());
            aiCallRecordService.markFailure(begin, AiCallState.TIMEOUT, e.getMessage());
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI));
        }

        Object codeObj = body.get("code");
        int code = codeObj instanceof Number ? ((Number) codeObj).intValue() : -1;
        if (code != 200) {
            aiCallRecordService.markFailure(begin, AiCallState.FAILED, "mascot error code=" + code);
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            String msg = body.get("msg") != null ? String.valueOf(body.get("msg")) : "mascot error";
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI, msg));
        }

        Map<String, Object> moduleData = gatewayModuleData(body);
        if (moduleData == null) {
            aiCallRecordService.markFailure(begin, AiCallState.FAILED, "mascot gateway response invalid");
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI));
        }
        AiModelUsageDTO usage = parseUsage(body, fallbackModel);
        Map<String, Object> billing;
        try {
            billing = billMascotUsage(begin, user, skill, usage, billingRelatedId, usePoints,
                    System.currentTimeMillis() - startMs);
        } catch (ApplicationException ex) {
            aiCallRecordService.markFailure(begin, AiCallState.FAILED, ex.getMessage());
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            throw ex;
        }

        String reply = moduleData.get("reply") != null ? String.valueOf(moduleData.get("reply")) : "";
        String imageUrl = "";
        if (isImageAction(moduleData)) {
            String imagePrompt = String.valueOf(moduleData.getOrDefault("imagePrompt", "")).trim();
            if (imagePrompt.isBlank()) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI, "生图提示词不能为空"));
            }
            AiImageResponseVO image = delegateMascotImage(user, request, imagePrompt, pySessionKey, dbSessionId);
            imageUrl = image.getUrl();
        }
        if (!ephemeral && dbSessionId != null) {
            if (!reply.isBlank()) {
                companionMemoryService.appendTextMessage(dbSessionId, "assistant", reply);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", pySessionKey);
        data.put("reply", reply);
        data.put("imageUrl", imageUrl);
        data.put("live2d", moduleData.get("live2d") instanceof Map ? moduleData.get("live2d") : Map.of());
        data.put("suggestedAppearance", moduleData.get("suggestedAppearance"));
        data.put("tier", vip ? "vip" : "basic");
        data.put("pointsCost", billing.get("pointsCost"));
        data.put("balanceAfter", billing.get("balanceAfter"));
        data.put("billingMode", billing.get("billingMode"));
        data.put("usageStats", billing.get("usageStats"));
        data.put("modelCode", usage.getModelCode());
        data.put("estimated", usage.getEstimated());
        return MascotConverter.toChatResponse(data);
    }

    private String mascotStreamAiUrl() {
        if (mascotAiUrl == null || mascotAiUrl.isBlank()) {
            return "http://localhost:5000/api/v1/gateway/stream";
        }
        String u = mascotAiUrl.trim();
        return u.endsWith("/invoke") ? u.substring(0, u.length() - "/invoke".length()) + "/stream" : u;
    }

    private Map<String, Object> gatewayRequest(Map<String, Object> payload) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("taskType", "MASCOT");
        request.put("intent", "CHAT");
        request.put("version", "v1");
        request.put("userContext", Collections.emptyMap());
        request.put("payload", payload);
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> gatewayModuleData(Map body) {
        if (!(body.get("data") instanceof Map<?, ?> gateway)
                || !Boolean.TRUE.equals(gateway.get("success"))
                || !(gateway.get("data") instanceof Map<?, ?> data)) {
            return null;
        }
        Map<String, Object> normalized = new HashMap<>();
        data.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        Object usage = gateway.get("usage");
        body.put("usage", usage instanceof Map ? usage : Map.of());
        return normalized;
    }

    private void sendMascotSse(SseEmitter emitter, Map<String, Object> payload) throws Exception {
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    @Override
    public void streamChat(User user, MascotChatRequest request, SseEmitter emitter) {
        String skill = normalizeSkill(request);
        boolean ephemeral = Boolean.TRUE.equals(request.getEphemeral());
        boolean vip = isVip(user);
        boolean usePoints = Boolean.TRUE.equals(request.getUsePointsBilling());
        boolean reservedBasic = false;
        String route = normalizeLlmRoute(resolveLlmRoute(request, vip, skill, user));
        String fallbackModel = aiPointsBillingService.resolveModelFromRoute(route);

        if (usePoints) {
            try {
                aiPointsBillingService.ensureBalance(user,
                        aiPointsBillingService.estimatePoints(fallbackModel,
                                Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS,
                                Constant.AI_ESTIMATE_CHAT_OUTPUT_TOKENS, 0));
            } catch (ApplicationException ex) {
                try {
                    sendMascotSse(emitter, Map.of("error", ex.getMessage() != null ? ex.getMessage() : "balance"));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(ex);
                }
                return;
            }
        } else if (!vip) {
            try {
                reserveBasicSlot(user.getId());
                reservedBasic = true;
            } catch (ApplicationException ex) {
                try {
                    sendMascotSse(emitter, Map.of("error", ex.getMessage() != null ? ex.getMessage() : "quota"));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(ex);
                }
                return;
            }
        }

        boolean[] reservedQwenFlash = {false};
        boolean[] reservedAdvanced = {false};
        if (!usePoints) {
            try {
                reserveUsageQuota(user, skill, route, reservedQwenFlash, reservedAdvanced);
            } catch (ApplicationException ex) {
                if (reservedBasic) {
                    releaseBasicSlot(user.getId());
                }
                try {
                    sendMascotSse(emitter, Map.of("error", ex.getMessage() != null ? ex.getMessage() : "quota"));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(ex);
                }
                return;
            }
        }

        Long dbSessionId = null;
        List<MascotHistoryTurn> mergedHistory;
        if (ephemeral) {
            mergedHistory = request.getHistory() != null ? request.getHistory() : List.of();
        } else {
            dbSessionId = companionMemoryService.ensureSession(user.getId(), skill, request.getSessionId());
            List<MascotHistoryTurn> dbHistory = companionMemoryService.loadHistoryTurns(dbSessionId, 16);
            mergedHistory = dbHistory.isEmpty()
                    ? (request.getHistory() != null ? request.getHistory() : List.of())
                    : dbHistory;
        }
        String pySessionKey = ephemeral
                ? (request.getSessionId() != null && !request.getSessionId().isBlank()
                ? request.getSessionId().trim() : String.valueOf(user.getId()))
                : String.valueOf(dbSessionId);
        final Long persistSessionId = dbSessionId;
        final String userMessage = request.getMessage().trim();
        final StringBuilder replyBuffer = new StringBuilder();
        final AtomicReference<String> streamSearchImageUrl = new AtomicReference<>();
        boolean imageRequested = false;
        String imagePrompt = "";
        final String billingRelatedId = billingRelatedId(request, pySessionKey);
        final AiCallBeginResult streamBegin = aiCallRecordService.beginCall(
                user.getId(), featureCode(skill), request.getClientRequestId(), fallbackModel);
        try {
            rejectDuplicateMascotBegin(streamBegin);
        } catch (ApplicationException ex) {
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            try {
                sendMascotSse(emitter, Map.of("error", ex.getMessage() != null ? ex.getMessage() : "duplicate"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(ex);
            }
            return;
        }
        final long streamStartMs = System.currentTimeMillis();

        if (!ephemeral && persistSessionId != null) {
            companionMemoryService.appendTextMessage(persistSessionId, "user", userMessage);
            try {
                sendMascotSse(emitter, Map.of("meta", Map.of("sessionId", String.valueOf(persistSessionId))));
            } catch (Exception ex) {
                log.warn("推送会话 id 失败: {}", ex.getMessage());
            }
        }

        Map<String, Object> pyBody = new HashMap<>();
        pyBody.put("message", userMessage);
        pyBody.put("session_id", pySessionKey);
        pyBody.put("appearance", normalizeAppearanceForPy(request));
        pyBody.put("tier", vip ? "vip" : "basic");
        int vipTier = user.getVipTier() != null ? user.getVipTier().intValue() : 0;
        if (vip && vipTier <= 0) {
            vipTier = 1;
        }
        pyBody.put("vip_tier", vipTier);
        pyBody.put("skill", skill);
        pyBody.put("history", toPyHistory(mergedHistory));
        pyBody.put("llm_provider", route);
        if (request.getClientDatetime() != null && !request.getClientDatetime().isBlank()) {
            pyBody.put("client_datetime", request.getClientDatetime().trim());
        }

        CompletableFuture<List<Map<String, Object>>> relatedFuture = null;
        if ("writing".equals(skill) || "help".equals(skill) || "chat".equals(skill)) {
            String ragQuery = userMessage;
            List<Long> excludeIds = request.getExcludeArticleIds() != null
                    ? request.getExcludeArticleIds() : List.of();
            relatedFuture = CompletableFuture.supplyAsync(
                    () -> mascotArticleRagHelper.recommendRelatedArticles(ragQuery, excludeIds));
        }

        final AtomicBoolean relatedMetaSent = new AtomicBoolean(false);
        if (relatedFuture != null) {
            relatedFuture.whenComplete((related, ex) -> {
                if (ex != null || related == null || related.isEmpty()) {
                    return;
                }
                if (relatedMetaSent.compareAndSet(false, true)) {
                    try {
                        Map<String, Object> earlyMeta = new LinkedHashMap<>();
                        earlyMeta.put("relatedArticles", related);
                        sendMascotSse(emitter, Map.of("meta", earlyMeta));
                    } catch (Exception sendEx) {
                        log.warn("推送相关帖子 meta 失败: {}", sendEx.getMessage());
                    }
                }
            });
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(mascotStreamAiUrl()).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if (internalKey != null && !internalKey.isBlank()) {
                conn.setRequestProperty("X-Internal-Key", internalKey);
            }
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(180_000);
            byte[] body = objectMapper.writeValueAsBytes(gatewayRequest(pyBody));
            conn.getOutputStream().write(body);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                if (reservedBasic) {
                    releaseBasicSlot(user.getId());
                }
                releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
                aiCallRecordService.markFailure(streamBegin, AiCallState.TIMEOUT, "stream http " + code);
                persistCompanionAssistantReply(ephemeral, persistSessionId, replyBuffer, streamSearchImageUrl.get());
                sendMascotSse(emitter, Map.of("error", "AI 服务暂时不可用"));
                emitter.complete();
                return;
            }

            AiModelUsageDTO usage = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> chunk = objectMapper.readValue(payload, Map.class);
                    if ("error".equals(chunk.get("type")) && chunk.get("data") instanceof Map<?, ?> errorData) {
                        Object message = errorData.get("message");
                        sendMascotSse(emitter, Map.of("error", message != null ? String.valueOf(message) : "AI 服务暂时不可用"));
                        break;
                    }
                    if ("final".equals(chunk.get("type")) && chunk.get("data") instanceof Map<?, ?> gateway) {
                        if (!(gateway.get("data") instanceof Map<?, ?> rawData)) {
                            continue;
                        }
                        Map<String, Object> finalData = new HashMap<>();
                        rawData.forEach((key, value) -> finalData.put(String.valueOf(key), value));
                        if (isImageAction(finalData)) {
                            imageRequested = true;
                            imagePrompt = String.valueOf(finalData.getOrDefault("imagePrompt", "")).trim();
                        }
                        Object reply = finalData.get("reply");
                        if (reply != null && !String.valueOf(reply).isEmpty()) {
                            String text = String.valueOf(reply);
                            replyBuffer.append(text);
                            sendMascotSse(emitter, Map.of("text", text));
                        }
                        Object usageObj = gateway.get("usage");
                        if (usageObj instanceof Map<?, ?> usageMap) {
                            Map<String, Object> normalizedUsage = new HashMap<>();
                            usageMap.forEach((key, value) -> normalizedUsage.put(String.valueOf(key), value));
                            usage = parseUsage(Map.of("usage", normalizedUsage), fallbackModel);
                        }
                        continue;
                    }
                    if (chunk.get("error") != null) {
                        sendMascotSse(emitter, Map.of("error", String.valueOf(chunk.get("error"))));
                        break;
                    }
                    Object textObj = chunk.get("text");
                    if (textObj != null) {
                        String piece = String.valueOf(textObj);
                        if (!piece.isEmpty()) {
                            replyBuffer.append(piece);
                            sendMascotSse(emitter, Map.of("text", piece));
                        }
                    }
                    Object metaObj = chunk.get("meta");
                    if (metaObj instanceof Map<?, ?> mm) {
                        Map<String, Object> metaMap = new HashMap<>();
                        mm.forEach((k, v) -> metaMap.put(String.valueOf(k), v));
                        Object searchImg = metaMap.get("searchImageUrl");
                        if (searchImg != null) {
                            String u = String.valueOf(searchImg).trim();
                            if (u.startsWith("http") && u.length() <= 1024) {
                                streamSearchImageUrl.set(u);
                            }
                        }
                        sendMascotSse(emitter, Map.of("meta", metaMap));
                    }
                    Object usageObj = chunk.get("usage");
                    if (usageObj instanceof Map<?, ?> um) {
                        Map<String, Object> usageMap = new HashMap<>();
                        um.forEach((k, v) -> usageMap.put(String.valueOf(k), v));
                        usage = parseUsage(Map.of("usage", usageMap), fallbackModel);
                    }
                }
            }

            if (usage == null) {
                usage = aiPointsBillingService.normalizeUsage(new AiModelUsageDTO(), fallbackModel);
                usage.setEstimated(true);
            }
            Map<String, Object> billing;
            try {
                billing = billMascotUsage(streamBegin, user, skill, usage, billingRelatedId, usePoints,
                        System.currentTimeMillis() - streamStartMs);
            } catch (ApplicationException ex) {
                if (reservedBasic) {
                    releaseBasicSlot(user.getId());
                }
                releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
                aiCallRecordService.markFailure(streamBegin, AiCallState.FAILED, ex.getMessage());
                persistCompanionAssistantReply(ephemeral, persistSessionId, replyBuffer, streamSearchImageUrl.get());
                sendMascotSse(emitter, Map.of("error", ex.getMessage() != null ? ex.getMessage() : "charge failed"));
                emitter.complete();
                return;
            }

            AiImageResponseVO delegatedImage = null;
            if (imageRequested) {
                if (imagePrompt.isBlank()) {
                    sendMascotSse(emitter, Map.of("error", "生图提示词不能为空"));
                    emitter.complete();
                    return;
                }
                sendMascotSse(emitter, Map.of("meta", Map.of("imageGenerating", true)));
                try {
                    delegatedImage = delegateMascotImage(user, request, imagePrompt, pySessionKey, persistSessionId);
                } catch (ApplicationException ex) {
                    sendMascotSse(emitter, Map.of("error", ex.getMessage() != null ? ex.getMessage() : "生图失败"));
                    emitter.complete();
                    return;
                }
                Map<String, Object> imageMeta = new LinkedHashMap<>();
                imageMeta.put("imageUrl", delegatedImage.getUrl());
                imageMeta.put("usageStats", delegatedImage.getUsageStats());
                imageMeta.put("pointsCost", delegatedImage.getPointsCost());
                imageMeta.put("balanceAfter", delegatedImage.getBalanceAfter());
                imageMeta.put("billingMode", delegatedImage.getBillingMode());
                sendMascotSse(emitter, Map.of("meta", imageMeta));
            }

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("sessionId", pySessionKey);
            meta.put("pointsCost", billing.get("pointsCost"));
            meta.put("balanceAfter", billing.get("balanceAfter"));
            meta.put("billingMode", billing.get("billingMode"));
            meta.put("usageStats", billing.get("usageStats"));
            meta.put("modelCode", usage.getModelCode());
            meta.put("llmRoute", route);
            if (relatedFuture != null) {
                try {
                    List<Map<String, Object>> related = relatedFuture.get(12, TimeUnit.SECONDS);
                    if (related != null && !related.isEmpty()) {
                        meta.put("relatedArticles", related);
                        relatedMetaSent.set(true);
                    }
                } catch (Exception ex) {
                    log.warn("看板娘帖子推荐超时或失败: {}", ex.getMessage());
                }
            }
            persistCompanionAssistantReply(ephemeral, persistSessionId, replyBuffer, streamSearchImageUrl.get());
            sendMascotSse(emitter, Map.of("meta", meta));
            emitter.complete();
        } catch (Exception e) {
            log.warn("看板娘流式调用失败: {}", e.getMessage());
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedQwenFlash[0], reservedAdvanced[0]);
            if (replyBuffer.length() > 0) {
                aiCallRecordService.settlePartialOutput(
                        streamBegin, user, featureCode(skill), fallbackModel,
                        replyBuffer.length(), billingRelatedId,
                        Constant.POINTS_SOURCE_AI_COMPANION, usePoints);
            } else {
                aiCallRecordService.markFailure(streamBegin, AiCallState.DISCONNECTED, e.getMessage());
            }
            persistCompanionAssistantReply(ephemeral, persistSessionId, replyBuffer, streamSearchImageUrl.get());
            try {
                sendMascotSse(emitter, Map.of("error", "对话失败，请稍后重试"));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(e);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void persistCompanionAssistantReply(
            boolean ephemeral, Long sessionId, CharSequence reply, String searchImageUrl) {
        if (ephemeral || sessionId == null || reply == null || reply.length() == 0) {
            return;
        }
        companionMemoryService.appendTextMessage(sessionId, "assistant", reply.toString(), searchImageUrl);
    }
}
