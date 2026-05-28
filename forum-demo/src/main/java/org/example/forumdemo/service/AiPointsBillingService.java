package org.example.forumdemo.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.ForumAiModelPrice;
import org.example.forumdemo.entity.db.ForumAiUsageLog;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.ai.AiModelUsageDTO;
import org.example.forumdemo.mapper.ForumAiModelPriceMapper;
import org.example.forumdemo.mapper.ForumAiModelUsageDailyMapper;
import org.example.forumdemo.mapper.ForumAiUsageLogMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.points.PointsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 按 forum_ai_model_price 折算积分并扣费（1元=100积分，向上取整）。
 */
@Slf4j
@Service
public class AiPointsBillingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal POINTS_PER_YUAN = new BigDecimal("100");

    @Resource
    private ForumAiModelPriceMapper forumAiModelPriceMapper;

    @Resource
    private ForumAiUsageLogMapper forumAiUsageLogMapper;

    @Resource
    private ForumAiModelUsageDailyMapper forumAiModelUsageDailyMapper;

    @Resource
    private PointsService pointsService;

    @Resource
    private UserMapper userMapper;

    private volatile Map<String, Map<String, BigDecimal>> priceCache;

    private Map<String, Map<String, BigDecimal>> loadPriceCache() {
        Map<String, Map<String, BigDecimal>> map = new HashMap<>();
        List<ForumAiModelPrice> rows = forumAiModelPriceMapper.selectList(
                Wrappers.lambdaQuery(ForumAiModelPrice.class).eq(ForumAiModelPrice::getEnabled, 1));
        for (ForumAiModelPrice p : rows) {
            map.computeIfAbsent(p.getModelCode(), k -> new HashMap<>())
                    .put(p.getBillUnit(), p.getPriceYuan());
        }
        return map;
    }

    private Map<String, BigDecimal> pricesForModel(String modelCode) {
        if (priceCache == null) {
            synchronized (this) {
                if (priceCache == null) {
                    priceCache = loadPriceCache();
                }
            }
        }
        return priceCache.getOrDefault(modelCode, Map.of());
    }

    public void refreshPriceCache() {
        priceCache = loadPriceCache();
    }

    /**
     * 估算积分（不扣费），用于前端展示。
     */
    public int estimatePoints(String modelCode, int inputTokens, int outputTokens, int imageCount) {
        AiModelUsageDTO u = new AiModelUsageDTO();
        u.setModelCode(modelCode);
        u.setInputTokens(inputTokens);
        u.setOutputTokens(outputTokens);
        u.setImageCount(imageCount);
        u.setEstimated(true);
        return calcPoints(u);
    }

    public int calcPoints(AiModelUsageDTO usage) {
        if (usage == null || usage.getModelCode() == null || usage.getModelCode().isBlank()) {
            return 0;
        }
        String model = usage.getModelCode().trim();
        Map<String, BigDecimal> units = pricesForModel(model);
        if (units.isEmpty()) {
            log.warn("未配置模型单价: {}", model);
            return 1;
        }
        BigDecimal yuan = BigDecimal.ZERO;
        int in = Math.max(0, usage.getInputTokens() != null ? usage.getInputTokens() : 0);
        int out = Math.max(0, usage.getOutputTokens() != null ? usage.getOutputTokens() : 0);
        int img = Math.max(0, usage.getImageCount() != null ? usage.getImageCount() : 0);

        if (in > 0 && units.containsKey("per_1m_input")) {
            yuan = yuan.add(units.get("per_1m_input")
                    .multiply(BigDecimal.valueOf(in))
                    .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP));
        }
        if (out > 0 && units.containsKey("per_1m_output")) {
            yuan = yuan.add(units.get("per_1m_output")
                    .multiply(BigDecimal.valueOf(out))
                    .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP));
        }
        if (img > 0 && units.containsKey("per_image")) {
            yuan = yuan.add(units.get("per_image").multiply(BigDecimal.valueOf(img)));
        }
        if (yuan.compareTo(BigDecimal.ZERO) <= 0 && units.containsKey("per_call")) {
            yuan = units.get("per_call");
        }
        if (yuan.compareTo(BigDecimal.ZERO) <= 0) {
            return 1;
        }
        return yuan.multiply(POINTS_PER_YUAN).setScale(0, RoundingMode.CEILING).intValue();
    }

    public AiModelUsageDTO normalizeUsage(AiModelUsageDTO raw, String fallbackModel) {
        AiModelUsageDTO u = raw != null ? raw : new AiModelUsageDTO();
        if (u.getModelCode() == null || u.getModelCode().isBlank()) {
            u.setModelCode(fallbackModel);
        }
        boolean est = Boolean.TRUE.equals(u.getEstimated());
        if ((u.getInputTokens() == null || u.getInputTokens() == 0)
                && (u.getOutputTokens() == null || u.getOutputTokens() == 0)
                && (u.getImageCount() == null || u.getImageCount() == 0)) {
            u.setInputTokens(Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS);
            u.setOutputTokens(Constant.AI_ESTIMATE_CHAT_OUTPUT_TOKENS);
            est = true;
        }
        u.setEstimated(est);
        return u;
    }

    public AiModelUsageDTO usageForImage(String modelCode, int images) {
        AiModelUsageDTO u = new AiModelUsageDTO();
        u.setModelCode(modelCode);
        u.setImageCount(Math.max(1, images));
        u.setInputTokens(0);
        u.setOutputTokens(0);
        u.setEstimated(false);
        return u;
    }

    public void ensureBalance(User user, int pointsNeeded) {
        if (pointsNeeded <= 0) {
            return;
        }
        User fresh = userMapper.selectById(user.getId());
        int bal = fresh != null && fresh.getPoints() != null ? fresh.getPoints() : 0;
        if (bal < pointsNeeded) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_POINTS_NOT_ENOUGH));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int charge(User user, String featureCode, AiModelUsageDTO usage, String relatedId, Byte pointsSource) {
        AiModelUsageDTO u = usage;
        int cost = calcPoints(u);
        if (cost <= 0) {
            cost = 1;
        }
        ensureBalance(user, cost);

        String remark = featureRemark(featureCode, u.getModelCode(), cost);
        int balanceAfter = pointsService.deductPoints(user.getId(), cost, pointsSource, null, remark);

        ForumAiUsageLog logRow = new ForumAiUsageLog();
        logRow.setUserId(user.getId());
        logRow.setFeatureCode(featureCode);
        logRow.setModelCode(u.getModelCode());
        logRow.setInputTokens(u.getInputTokens() != null ? u.getInputTokens() : 0);
        logRow.setOutputTokens(u.getOutputTokens() != null ? u.getOutputTokens() : 0);
        logRow.setImageCount(u.getImageCount() != null ? u.getImageCount() : 0);
        logRow.setPointsCost(cost);
        logRow.setEstimated(Boolean.TRUE.equals(u.getEstimated()) ? (byte) 1 : (byte) 0);
        logRow.setRelatedId(relatedId);
        logRow.setDeleteState((byte) 0);
        forumAiUsageLogMapper.insert(logRow);

        LocalDate today = LocalDate.now(ZONE);
        forumAiModelUsageDailyMapper.incrementUsage(
                Date.valueOf(today),
                u.getModelCode(),
                cost,
                logRow.getInputTokens(),
                logRow.getOutputTokens(),
                logRow.getImageCount());

        return balanceAfter;
    }

    public String resolveModelFromRoute(String llmRoute) {
        if (llmRoute == null || llmRoute.isBlank()) {
            return "qwen3.6-flash";
        }
        return switch (llmRoute.trim().toLowerCase(Locale.ROOT)) {
            case "qwen-flash" -> "qwen3.6-flash";
            case "qwen-deep" -> Constant.AI_MODEL_QWEN_DEEP;
            case "deepseek-flash" -> "deepseek-v4-flash";
            case "deepseek-deep" -> "deepseek-v4-pro";
            case "gemini-deep" -> Constant.AI_MODEL_GEMINI_DEEP;
            case "claude-haiku" -> Constant.AI_MODEL_CLAUDE_HAIKU;
            case "claude-sonnet" -> Constant.AI_MODEL_CLAUDE_SONNET;
            case "gpt-image-2" -> Constant.AI_MODEL_IMAGE_PREMIUM;
            default -> llmRoute;
        };
    }

    private static String featureRemark(String feature, String model, int cost) {
        return "AI消耗·" + feature + "·" + model + "·" + cost + "积分";
    }
}
