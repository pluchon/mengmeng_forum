package org.pluchon.forum.service.impl.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.BillableFeature;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.metrics.ForumMetrics;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.ForumAiModelPrice;
import org.pluchon.forum.entity.db.ForumAiUsageLog;
import org.pluchon.forum.entity.dto.AiModelUsageDTO;
import org.pluchon.forum.api.economy.VipTierSnapshotVO;
import org.pluchon.forum.cloud.feign.AiPointsInternalFeignClient;
import org.pluchon.forum.cloud.feign.AiVipInternalFeignClient;
import org.pluchon.forum.mapper.ForumAiModelPriceMapper;
import org.pluchon.forum.mapper.ForumAiModelUsageDailyMapper;
import org.pluchon.forum.mapper.ForumAiUsageLogMapper;
import org.pluchon.forum.service.interfaces.ai.AiQuotaService;
import org.pluchon.forum.service.security.AiUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 按 forum_ai_model_price 折算积分并扣费 1元 100积分，向上取整
@Slf4j
@Service
public class AiPointsBillingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");
    private static final BigDecimal POINTS_PER_YUAN = new BigDecimal("100");

    @Resource
    private ForumAiModelPriceMapper forumAiModelPriceMapper;

    @Resource
    private ForumAiUsageLogMapper forumAiUsageLogMapper;

    @Resource
    private ForumAiModelUsageDailyMapper forumAiModelUsageDailyMapper;

    @Resource
    private AiPointsInternalFeignClient aiPointsInternalFeignClient;

    @Resource
    private AiVipInternalFeignClient vipInternalFeignClient;

    @Resource
    private ForumMetrics forumMetrics;

    @Resource
    private AiQuotaService aiQuotaService;

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

    // 估算积分 不扣费 ，用于前端展示
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
        return calcYuan(usage).multiply(POINTS_PER_YUAN)
                .setScale(0, RoundingMode.CEILING).intValue();
    }

    public BigDecimal calcYuan(AiModelUsageDTO usage) {
        AiModelUsageDTO u = usageForBillingCalc(usage);
        if (u.getModelCode() == null || u.getModelCode().isBlank()) {
            return BigDecimal.ZERO;
        }
        String model = u.getModelCode().trim();
        Map<String, BigDecimal> units = pricesForModel(model);
        if (units.isEmpty()) {
            log.warn("未配置模型单价: {}", model);
            return BigDecimal.ZERO;
        }
        BigDecimal yuan = BigDecimal.ZERO;
        int in = Math.max(0, u.getInputTokens() != null ? u.getInputTokens() : 0);
        int out = Math.max(0, u.getOutputTokens() != null ? u.getOutputTokens() : 0);
        int img = Math.max(0, u.getImageCount() != null ? u.getImageCount() : 0);

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
            return BigDecimal.ZERO;
        }
        return yuan.setScale(8, RoundingMode.HALF_UP);
    }

    public AiModelUsageDTO normalizeUsage(AiModelUsageDTO raw, String fallbackModel) {
        AiModelUsageDTO u = raw != null ? raw : new AiModelUsageDTO();
        if (u.getModelCode() == null || u.getModelCode().isBlank()) {
            u.setModelCode(fallbackModel);
        }
        int in = u.getInputTokens() != null ? u.getInputTokens() : 0;
        int out = u.getOutputTokens() != null ? u.getOutputTokens() : 0;
        int img = u.getImageCount() != null ? u.getImageCount() : 0;
        if (in > 0 || out > 0 || img > 0) {
            if (u.getEstimated() == null) {
                u.setEstimated(false);
            }
        } else {
            u.setEstimated(true);
        }
        return u;
    }

    // 计费折算用：无厂商 token 时用预估常量，不污染展示字段
    private AiModelUsageDTO usageForBillingCalc(AiModelUsageDTO usage) {
        AiModelUsageDTO u = usage;
        int in = u.getInputTokens() != null ? u.getInputTokens() : 0;
        int out = u.getOutputTokens() != null ? u.getOutputTokens() : 0;
        int img = u.getImageCount() != null ? u.getImageCount() : 0;
        if (in == 0 && out == 0 && img == 0) {
            AiModelUsageDTO copy = new AiModelUsageDTO();
            copy.setModelCode(u.getModelCode());
            copy.setInputTokens(Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS);
            copy.setOutputTokens(Constant.AI_ESTIMATE_CHAT_OUTPUT_TOKENS);
            copy.setImageCount(img);
            copy.setEstimated(true);
            copy.setLatencyMs(u.getLatencyMs());
            return copy;
        }
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

    // 多模型子图用量汇总，仅用于逻辑调用记录与前端摘要
    public AiModelUsageDTO aggregateUsage(List<AiModelUsageDTO> usages, String fallbackModel) {
        List<AiModelUsageDTO> normalized = normalizeUsageBatch(usages, fallbackModel);
        AiModelUsageDTO total = new AiModelUsageDTO();
        total.setModelCode(normalized.isEmpty()
                ? fallbackModel : normalized.get(normalized.size() - 1).getModelCode());
        total.setInputTokens(normalized.stream()
                .mapToInt(item -> item.getInputTokens() != null ? item.getInputTokens() : 0).sum());
        total.setOutputTokens(normalized.stream()
                .mapToInt(item -> item.getOutputTokens() != null ? item.getOutputTokens() : 0).sum());
        total.setImageCount(normalized.stream()
                .mapToInt(item -> item.getImageCount() != null ? item.getImageCount() : 0).sum());
        total.setLatencyMs(normalized.stream()
                .mapToInt(item -> item.getLatencyMs() != null ? item.getLatencyMs() : 0).sum());
        total.setEstimated(normalized.stream().anyMatch(item -> Boolean.TRUE.equals(item.getEstimated())));
        return total;
    }

    // 有效会员：vip_tier&gt;0 且未过期 不扣萌萌币，走日额度 + token 审计
    public boolean vipExemptPoints(AiUserContext user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        VipTierSnapshotVO snapshot = vipInternalFeignClient.tierSnapshot(user.getId());
        return snapshot != null && snapshot.isVipActive();
    }

    public void ensureBalance(AiUserContext user, int pointsNeeded) {
        if (pointsNeeded <= 0) {
            return;
        }
        int bal = balanceOf(user.getId());
        if (bal < pointsNeeded) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_POINTS_NOT_ENOUGH));
        }
    }

    // 前端展示：token / 耗时 / 计费方式 数据来自 ai server 厂商 usage 字段
    public Map<String, Object> buildUsageStats(AiModelUsageDTO usage, int pointsCharged, String billingMode) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (usage == null) {
            return m;
        }
        m.put("modelCode", usage.getModelCode());
        m.put("inputTokens", usage.getInputTokens() != null ? usage.getInputTokens() : 0);
        m.put("outputTokens", usage.getOutputTokens() != null ? usage.getOutputTokens() : 0);
        m.put("imageCount", usage.getImageCount() != null ? usage.getImageCount() : 0);
        m.put("latencyMs", usage.getLatencyMs() != null ? usage.getLatencyMs() : 0);
        m.put("estimated", Boolean.TRUE.equals(usage.getEstimated()));
        m.put("billingMode", billingMode != null ? billingMode : "quota");
        m.put("pointsCost", pointsCharged);
        return m;
    }

    public void applyLatencyFromMap(AiModelUsageDTO usage, Map<?, ?> um) {
        if (usage == null || um == null) {
            return;
        }
        Object lm = um.get("latency_ms");
        if (lm == null) {
            lm = um.get("latencyMs");
        }
        if (lm instanceof Number n) {
            usage.setLatencyMs(n.intValue());
        } else if (lm != null) {
            try {
                usage.setLatencyMs(Integer.parseInt(String.valueOf(lm)));
            } catch (NumberFormatException ignored) {
                // 忽略
            }
        }
    }

    // 统一计费：默认按会员/免费日额度，不扣萌萌币；仅 usePointsBilling true 时扣积分
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bill(AiUserContext user, String featureCode, AiModelUsageDTO usage, String relatedId,
                                    Byte pointsSource, boolean usePointsBilling) {
        if (relatedId != null && !relatedId.isBlank()) {
            Long dupCount = forumAiUsageLogMapper.selectCount(
                    Wrappers.lambdaQuery(ForumAiUsageLog.class)
                            .eq(ForumAiUsageLog::getUserId, user.getId())
                            .eq(ForumAiUsageLog::getFeatureCode, featureCode)
                            .eq(ForumAiUsageLog::getRelatedId, relatedId.trim()));
            if (dupCount != null && dupCount > 0) {
                forumMetrics.recordIdempotencyHit();
                int balanceAfter = balanceOf(user.getId());
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("pointsCost", 0);
                out.put("balanceAfter", balanceAfter);
                out.put("billingMode", "duplicate");
                out.put("usageStats", buildUsageStats(usage, 0, "duplicate"));
                return out;
            }
        }
        AiModelUsageDTO u = usage;
        int referenceCost = calcPoints(u);
        if (referenceCost <= 0) {
            referenceCost = 1;
        }
        boolean billable = BillableFeature.contains(featureCode);
        int charged = usePointsBilling && billable ? referenceCost : 0;
        int balanceAfter;
        String billingMode;
        if (usePointsBilling && billable) {
            balanceAfter = chargePointsAndLog(user, featureCode, u, relatedId, pointsSource, charged);
            billingMode = "points";
        } else {
            recordUsageOnly(user, featureCode, u, relatedId);
            balanceAfter = balanceOf(user.getId());
            billingMode = billable
                    ? (vipExemptPoints(user) ? "vip_quota" : "free_quota")
                : "platform_free";
        }
        if (billable && !usePointsBilling) {
            settleQuotaUsage(user, featureCode, List.of(u));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pointsCost", charged);
        out.put("balanceAfter", balanceAfter);
        out.put("billingMode", billingMode);
        out.put("usageStats", buildUsageStats(u, charged, billingMode));
        return out;
    }

    // 多模型子图统一结算：积分只扣一次，用量按实际模型分别持久化
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> billBatch(
            AiUserContext user,
            String featureCode,
            List<AiModelUsageDTO> usages,
            String fallbackModel,
            String relatedId,
            Byte pointsSource,
            boolean usePointsBilling) {
        List<AiModelUsageDTO> normalized = normalizeUsageBatch(usages, fallbackModel);
        if (normalized.isEmpty()) {
            normalized = List.of(normalizeUsage(null, fallbackModel));
        }
        String firstRelatedId = derivedUsageRelatedId(relatedId, 0);
        if (firstRelatedId != null) {
            Long duplicate = forumAiUsageLogMapper.selectCount(
                    Wrappers.lambdaQuery(ForumAiUsageLog.class)
                            .eq(ForumAiUsageLog::getUserId, user.getId())
                            .eq(ForumAiUsageLog::getFeatureCode, featureCode)
                            .eq(ForumAiUsageLog::getRelatedId, firstRelatedId));
            if (duplicate != null && duplicate > 0) {
                forumMetrics.recordIdempotencyHit();
                int balanceAfter = balanceOf(user.getId());
                AiModelUsageDTO total = aggregateUsage(normalized, fallbackModel);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("pointsCost", 0);
                out.put("balanceAfter", balanceAfter);
                out.put("billingMode", "duplicate");
                out.put("usageStats", buildUsageStats(total, 0, "duplicate"));
                return out;
            }
        }

        List<Integer> itemCosts = new ArrayList<>(normalized.size());
        int referenceCost = 0;
        for (AiModelUsageDTO usage : normalized) {
            int itemCost = Math.max(1, calcPoints(usage));
            itemCosts.add(itemCost);
            referenceCost += itemCost;
        }
        boolean billable = BillableFeature.contains(featureCode);
        int charged = usePointsBilling && billable ? referenceCost : 0;
        int balanceAfter;
        String billingMode;
        if (usePointsBilling && billable) {
            ensureBalance(user, charged);
            AiModelUsageDTO total = aggregateUsage(normalized, fallbackModel);
            String remark = featureRemark(featureCode, total.getModelCode(), charged);
            String idempotencyKey = StringUtils.hasText(relatedId)
                    ? "ai_bill:" + user.getId() + ":" + relatedId.trim() : null;
            Integer balance = aiPointsInternalFeignClient.deductPoints(
                    user.getId(), charged, pointsSource == null ? 0 : pointsSource,
                    null, remark, idempotencyKey);
            if (balance == null) {
                throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
            }
            balanceAfter = balance;
            billingMode = "points";
        } else {
            balanceAfter = balanceOf(user.getId());
            billingMode = billable
                    ? (vipExemptPoints(user) ? "vip_quota" : "free_quota")
                    : "platform_free";
        }

        for (int index = 0; index < normalized.size(); index++) {
            persistUsageLog(
                    user,
                    featureCode,
                    normalized.get(index),
                    derivedUsageRelatedId(relatedId, index),
                    usePointsBilling && billable ? itemCosts.get(index) : 0);
        }
        if (billable && !usePointsBilling) {
            settleQuotaUsage(user, featureCode, normalized);
        }
        AiModelUsageDTO total = aggregateUsage(normalized, fallbackModel);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pointsCost", charged);
        out.put("balanceAfter", balanceAfter);
        out.put("billingMode", billingMode);
        out.put("usageStats", buildUsageStats(total, charged, billingMode));
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bill(AiUserContext user, String featureCode, AiModelUsageDTO usage, String relatedId,
                                    Byte pointsSource) {
        return bill(user, featureCode, usage, relatedId, pointsSource, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public int charge(AiUserContext user, String featureCode, AiModelUsageDTO usage, String relatedId, Byte pointsSource) {
        Map<String, Object> r = bill(user, featureCode, usage, relatedId, pointsSource);
        return (int) r.get("balanceAfter");
    }

    private void recordUsageOnly(AiUserContext user, String featureCode, AiModelUsageDTO u, String relatedId) {
        persistUsageLog(user, featureCode, u, relatedId, 0);
    }

    private int chargePointsAndLog(AiUserContext user, String featureCode, AiModelUsageDTO u, String relatedId,
                                   Byte pointsSource, int cost) {
        ensureBalance(user, cost);
        String remark = featureRemark(featureCode, u.getModelCode(), cost);
        String idempotencyKey = relatedId != null && !relatedId.isBlank()
                ? "ai_bill:" + user.getId() + ":" + relatedId.trim()
                : null;
        Integer balance = aiPointsInternalFeignClient.deductPoints(
                user.getId(), cost, pointsSource == null ? 0 : pointsSource, null, remark, idempotencyKey);
        if (balance == null) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        int balanceAfter = balance;
        persistUsageLog(user, featureCode, u, relatedId, cost);
        return balanceAfter;
    }

    private int balanceOf(Long userId) {
        Integer balance = aiPointsInternalFeignClient.getBalance(userId);
        return balance == null ? 0 : balance;
    }

    private void persistUsageLog(AiUserContext user, String featureCode, AiModelUsageDTO u,
                                 String relatedId, int pointsCost) {
        ForumAiUsageLog logRow = new ForumAiUsageLog();
        logRow.setUserId(user.getId());
        logRow.setFeatureCode(featureCode);
        logRow.setModelCode(u.getModelCode());
        logRow.setInputTokens(u.getInputTokens() != null ? u.getInputTokens() : 0);
        logRow.setOutputTokens(u.getOutputTokens() != null ? u.getOutputTokens() : 0);
        logRow.setImageCount(u.getImageCount() != null ? u.getImageCount() : 0);
        logRow.setPointsCost(pointsCost);
        logRow.setEstimated(Boolean.TRUE.equals(u.getEstimated()) ? (byte) 1 : (byte) 0);
        logRow.setBillableState(BillableFeature.contains(featureCode) ? (byte) 1 : (byte) 0);
        logRow.setCostYuan(calcYuan(u));
        logRow.setQuotaPeriodKey(quotaPeriodKey(user));
        logRow.setRelatedId(relatedId);
        logRow.setDeleteState((byte) 0);
        forumAiUsageLogMapper.insert(logRow);

        LocalDate today = LocalDate.now(ZONE);
        forumAiModelUsageDailyMapper.incrementUsage(
                java.sql.Date.valueOf(today),
                u.getModelCode(),
                pointsCost,
                logRow.getInputTokens(),
                logRow.getOutputTokens(),
                logRow.getImageCount());
    }

    private String quotaPeriodKey(AiUserContext user) {
        if (user == null || !user.isVipActive() || user.getVipExpireAt() == null) {
            return LocalDate.now(ZONE).toString().substring(0, 7);
        }
        ZonedDateTime end = user.getVipExpireAt().toInstant().atZone(ZONE);
        return end.minusDays(30).toLocalDate() + "_" + end.toLocalDate();
    }

    private void settleQuotaUsage(AiUserContext user, String featureCode, List<AiModelUsageDTO> usages) {
        BigDecimal qwenCost = BigDecimal.ZERO;
        int wanImages = 0;
        for (AiModelUsageDTO usage : usages) {
            String model = usage.getModelCode() == null ? "" : usage.getModelCode().trim();
            int images = usage.getImageCount() == null ? 0 : Math.max(0, usage.getImageCount());
            if (model.startsWith("qwen")) {
                qwenCost = qwenCost.add(calcYuan(usage));
            }
            if (Constant.AI_MODEL_IMAGE_NORMAL.equals(model)
                    || Constant.AI_MODEL_IMAGE_DASH_PREMIUM.equals(model)
                    || "wan2.7-image".equals(model)
                    || model.startsWith("wan")) {
                wanImages += images;
            }
        }
        aiQuotaService.settleUsage(
                user,
                BillableFeature.usesQwenQuota(featureCode),
                qwenCost,
                wanImages);
    }

    private List<AiModelUsageDTO> normalizeUsageBatch(List<AiModelUsageDTO> usages, String fallbackModel) {
        Map<String, AiModelUsageDTO> totals = new LinkedHashMap<>();
        for (AiModelUsageDTO raw : usages != null ? usages : List.<AiModelUsageDTO>of()) {
            AiModelUsageDTO normalized = normalizeUsage(raw, fallbackModel);
            String model = StringUtils.hasText(normalized.getModelCode())
                    ? normalized.getModelCode().trim() : fallbackModel;
            AiModelUsageDTO bucket = totals.computeIfAbsent(model, key -> {
                AiModelUsageDTO created = new AiModelUsageDTO();
                created.setModelCode(key);
                created.setInputTokens(0);
                created.setOutputTokens(0);
                created.setImageCount(0);
                created.setLatencyMs(0);
                created.setEstimated(false);
                return created;
            });
            bucket.setInputTokens(bucket.getInputTokens()
                    + (normalized.getInputTokens() != null ? normalized.getInputTokens() : 0));
            bucket.setOutputTokens(bucket.getOutputTokens()
                    + (normalized.getOutputTokens() != null ? normalized.getOutputTokens() : 0));
            bucket.setImageCount(bucket.getImageCount()
                    + (normalized.getImageCount() != null ? normalized.getImageCount() : 0));
            bucket.setLatencyMs(bucket.getLatencyMs()
                    + (normalized.getLatencyMs() != null ? normalized.getLatencyMs() : 0));
            bucket.setEstimated(Boolean.TRUE.equals(bucket.getEstimated())
                    || Boolean.TRUE.equals(normalized.getEstimated()));
        }
        if (totals.isEmpty()) {
            AiModelUsageDTO fallback = Constant.AI_MODEL_IMAGE_NORMAL.equals(fallbackModel)
                    || Constant.AI_MODEL_IMAGE_DASH_PREMIUM.equals(fallbackModel)
                    || (fallbackModel != null && fallbackModel.startsWith("wan"))
                    ? usageForImage(fallbackModel, 1)
                    : normalizeUsage(new AiModelUsageDTO(), fallbackModel);
            totals.put(fallbackModel, fallback);
        }
        return new ArrayList<>(totals.values());
    }

    private String derivedUsageRelatedId(String relatedId, int index) {
        if (!StringUtils.hasText(relatedId)) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(relatedId.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return "g:" + hex.substring(0, 54) + ":" + index;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public String resolveModelFromRoute(String llmRoute) {
        if (llmRoute == null || llmRoute.isBlank()) {
            return "qwen3.7-flash";
        }
        return switch (llmRoute.trim().toLowerCase(Locale.ROOT)) {
            case "qwen-flash" -> "qwen3.7-flash";
            case "qwen-deep" -> Constant.AI_MODEL_QWEN_DEEP;
            default -> llmRoute;
        };
    }

    private static String featureRemark(String feature, String model, int cost) {
        return "AI消耗·" + feature + "·" + model + "·" + cost + "积分";
    }
}
