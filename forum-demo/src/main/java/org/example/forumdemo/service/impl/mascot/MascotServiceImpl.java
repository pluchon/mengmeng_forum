package org.example.forumdemo.service.impl.mascot;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.ai.AiModelUsageDTO;
import org.example.forumdemo.entity.dto.mascot.MascotChatRequest;
import org.example.forumdemo.entity.dto.mascot.MascotHistoryTurn;
import org.example.forumdemo.service.AiPointsBillingService;
import org.example.forumdemo.service.CompanionMemoryService;
import org.example.forumdemo.service.interfaces.ai.AiQuotaService;
import org.example.forumdemo.service.interfaces.mascot.MascotService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AiPointsBillingService aiPointsBillingService;

    @Resource
    private AiQuotaService aiQuotaService;

    @Resource
    private CompanionMemoryService companionMemoryService;

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

    private String resolveLlmRoute(MascotChatRequest request, boolean vip, String skill) {
        String route = request.getLlmProvider() != null ? request.getLlmProvider().trim().toLowerCase(Locale.ROOT) : "";
        if ("help".equals(skill)) {
            if (!vip || route.isBlank() || route.contains("deep")) {
                return "qwen-flash";
            }
        }
        if (route.isBlank()) {
            return "qwen-flash";
        }
        return route;
    }

    private String featureCode(String skill) {
        return switch (skill) {
            case "help" -> "companion_help";
            case "reading" -> "companion_reading";
            case "drawing" -> "companion_image";
            default -> "companion_writing";
        };
    }

    private void reserveAiQuota(User user, String route, boolean[] reservedDeepseek, boolean[] reservedAdvanced) {
        if (route.startsWith("deepseek")) {
            if (!aiQuotaService.hasUnlimitedDeepseek(user)) {
                aiQuotaService.consumeDeepseekWrite(user);
                reservedDeepseek[0] = true;
            }
        } else if (route.startsWith("gemini")) {
            aiQuotaService.consumeAdvancedLlm(user);
            reservedAdvanced[0] = true;
        }
    }

    private void releaseAiQuota(User user, boolean reservedDeepseek, boolean reservedAdvanced) {
        if (reservedDeepseek) {
            aiQuotaService.releaseDeepseekWrite(user);
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
        }
        return aiPointsBillingService.normalizeUsage(dto, fallbackModel);
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
    public Map<String, Object> chat(User user, MascotChatRequest request) {
        String skill = normalizeSkill(request);
        if ("reading".equals(skill)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI, "伴读功能开发中"));
        }

        boolean vip = isVip(user);
        boolean reservedBasic = false;
        if (!vip) {
            reserveBasicSlot(user.getId());
            reservedBasic = true;
        }

        String route = resolveLlmRoute(request, vip, skill);
        String fallbackModel = aiPointsBillingService.resolveModelFromRoute(route);
        aiPointsBillingService.ensureBalance(user,
                aiPointsBillingService.estimatePoints(fallbackModel,
                        Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS,
                        Constant.AI_ESTIMATE_CHAT_OUTPUT_TOKENS, 0));

        boolean[] reservedDeepseek = {false};
        boolean[] reservedAdvanced = {false};
        if (vip && "writing".equals(skill)) {
            try {
                reserveAiQuota(user, route, reservedDeepseek, reservedAdvanced);
            } catch (ApplicationException ex) {
                if (reservedBasic) {
                    releaseBasicSlot(user.getId());
                }
                throw ex;
            }
        }

        Long dbSessionId = companionMemoryService.ensureSession(user.getId(), skill, request.getSessionId());
        List<MascotHistoryTurn> dbHistory = companionMemoryService.loadHistoryTurns(dbSessionId, 16);
        List<MascotHistoryTurn> mergedHistory = dbHistory.isEmpty() ? request.getHistory() : dbHistory;

        Map<String, Object> pyBody = new HashMap<>();
        pyBody.put("message", request.getMessage().trim());
        pyBody.put("session_id", String.valueOf(dbSessionId));
        pyBody.put("appearance", normalizeAppearanceForPy(request));
        pyBody.put("tier", vip ? "vip" : "basic");
        pyBody.put("skill", skill);
        pyBody.put("history", toPyHistory(mergedHistory));
        pyBody.put("llm_provider", route);

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalKey != null && !internalKey.isBlank()) {
            headers.set("X-Internal-Key", internalKey);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(pyBody, headers);

        Map body;
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(mascotAiUrl, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("bad status");
            }
            body = response.getBody();
        } catch (ApplicationException ex) {
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedDeepseek[0], reservedAdvanced[0]);
            throw ex;
        } catch (Exception e) {
            log.warn("看板娘 Python 调用失败: {}", e.getMessage());
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedDeepseek[0], reservedAdvanced[0]);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI));
        }

        Object codeObj = body.get("code");
        int code = codeObj instanceof Number ? ((Number) codeObj).intValue() : -1;
        if (code != 200) {
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedDeepseek[0], reservedAdvanced[0]);
            String msg = body.get("msg") != null ? String.valueOf(body.get("msg")) : "mascot error";
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MASCOT_AI, msg));
        }

        AiModelUsageDTO usage = parseUsage(body, fallbackModel);
        int pointsCost = aiPointsBillingService.calcPoints(usage);
        int balanceAfter;
        try {
            balanceAfter = aiPointsBillingService.charge(
                    user,
                    featureCode(skill),
                    usage,
                    String.valueOf(dbSessionId),
                    Constant.POINTS_SOURCE_AI_COMPANION);
        } catch (ApplicationException ex) {
            if (reservedBasic) {
                releaseBasicSlot(user.getId());
            }
            releaseAiQuota(user, reservedDeepseek[0], reservedAdvanced[0]);
            throw ex;
        }

        String reply = body.get("reply") != null ? String.valueOf(body.get("reply")) : "";
        companionMemoryService.appendTextMessage(dbSessionId, "user", request.getMessage().trim());
        companionMemoryService.appendTextMessage(dbSessionId, "assistant", reply);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", String.valueOf(dbSessionId));
        data.put("reply", reply);
        data.put("live2d", body.get("live2d") instanceof Map ? body.get("live2d") : Map.of());
        data.put("suggestedAppearance", body.get("suggested_appearance"));
        data.put("tier", vip ? "vip" : "basic");
        data.put("pointsCost", pointsCost);
        data.put("balanceAfter", balanceAfter);
        data.put("modelCode", usage.getModelCode());
        data.put("estimated", usage.getEstimated());
        return data;
    }
}
