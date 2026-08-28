package org.pluchon.forum.service.impl.vip;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.pluchon.forum.api.ai.AiUsageDailyBucketsVO;
import org.pluchon.forum.api.economy.VipQuotaHintVO;
import org.pluchon.forum.cloud.feign.AiUsageInternalFeignClient;
import org.pluchon.forum.economy.client.EconomyUserInternalFeignClient;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.converter.VipPurchaseRecordConverter;
import org.pluchon.forum.entity.db.UserVipSubscription;
import org.pluchon.forum.entity.vo.vip.VipCenterVO;
import org.pluchon.forum.entity.vo.vip.VipPlanFeatureVO;
import org.pluchon.forum.entity.vo.vip.VipPlanVO;
import org.pluchon.forum.entity.vo.vip.VipPurchaseRecordVO;
import org.pluchon.forum.entity.vo.vip.VipQuotaPanelVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.mapper.VipPurchaseRecordMapper;
import org.pluchon.forum.entity.db.VipPurchaseRecord;
import org.pluchon.forum.service.interfaces.points.PointsService;
import org.pluchon.forum.service.interfaces.vip.VipCenterService;
import org.pluchon.forum.service.interfaces.vip.VipEntitlementService;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@Service
public class VipCenterServiceImpl implements VipCenterService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    @Resource
    private EconomyUserInternalFeignClient userInternalFeignClient;

    @Resource
    private VipEntitlementService vipEntitlementService;

    @Resource
    private PointsService pointsService;

    @Resource
    private AiUsageInternalFeignClient aiUsageInternalFeignClient;

    @Resource
    private VipPurchaseRecordMapper vipPurchaseRecordMapper;

    private boolean vipActive(UserVipSubscription sub) {
        if (sub == null) {
            return false;
        }
        Byte tier = sub.getVipTier();
        if (tier == null || tier == 0) {
            return false;
        }
        Date exp = sub.getVipExpireAt();
        if (exp == null) {
            return true;
        }
        return exp.after(new Date());
    }

    private int tierOrder(Byte tier) {
        return tier == null ? 0 : tier.intValue();
    }

    @Override
    public VipCenterVO center(Long userId) {
        requireUserExists(userId);
        UserVipSubscription sub = vipEntitlementService.getSubscription(userId);
        Byte vipTier = sub != null && sub.getVipTier() != null ? sub.getVipTier() : Constant.VIP_TIER_FREE;
        Date vipExpireAt = sub != null ? sub.getVipExpireAt() : null;
        boolean active = vipActive(sub);
        int cur = tierOrder(vipTier);

        VipCenterVO vo = new VipCenterVO();
        vo.setVipTier(vipTier);
        vo.setVipExpireAt(vipExpireAt);
        vo.setPoints(pointsService.getWallet(userId).getBalance());
        vo.setVipActive(active);
        boolean firstPurchaseEligible = vipPurchaseRecordMapper.selectCount(
                Wrappers.lambdaQuery(VipPurchaseRecord.class)
                        .eq(VipPurchaseRecord::getUserId, userId)
                        .eq(VipPurchaseRecord::getPaymentState, (byte) 1)
                        .eq(VipPurchaseRecord::getDeleteState, (byte) 0)) == 0;
        vo.setPlans(buildPlans(cur, active, firstPurchaseEligible));
        vo.setQuota(buildQuotaPanel(userId, active ? vipTier : Constant.VIP_TIER_FREE, sub));
        return vo;
    }

    @Override
    public VipQuotaHintVO quotaHintForLlmRoute(Long userId, String llmRoute) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("percent", 0);
        out.put("canUsePointsPay", false);
        out.put("quotaLabel", "");
        requireUserExists(userId);
        UserVipSubscription sub = vipEntitlementService.getSubscription(userId);
        boolean active = vipActive(sub);
        Byte vipTier = active ? sub.getVipTier() : Constant.VIP_TIER_FREE;
        VipQuotaPanelVO panel = buildQuotaPanel(userId, vipTier, sub);
        long limit = Math.max(0L, panel.getQwenBudgetMicros());
        long used = Math.max(0L, panel.getQwenUsedMicros());
        int percent = limit > 0L ? (int) Math.min(100L, Math.round(used * 100.0 / limit)) : 0;
        out.put("percent", percent);
        out.put("quotaLabel", "通用额度");
        out.put("canUsePointsPay", percent >= 100);
        return toQuotaHintVO(out);
    }

    private static VipQuotaHintVO toQuotaHintVO(Map<String, Object> raw) {
        VipQuotaHintVO vo = new VipQuotaHintVO();
        Object percent = raw.get("percent");
        vo.setPercent(percent instanceof Number n ? n.intValue() : 0);
        vo.setCanUsePointsPay(Boolean.TRUE.equals(raw.get("canUsePointsPay")));
        Object label = raw.get("quotaLabel");
        vo.setQuotaLabel(label == null ? "" : String.valueOf(label));
        return vo;
    }

    @Override
    public VipQuotaPanelVO quota(Long userId) {
        requireUserExists(userId);
        UserVipSubscription sub = vipEntitlementService.getSubscription(userId);
        Byte vipTier = sub != null ? sub.getVipTier() : Constant.VIP_TIER_FREE;
        return buildQuotaPanel(userId, vipActive(sub) ? vipTier : Constant.VIP_TIER_FREE, sub);
    }

    @Override
    public PageResult<VipPurchaseRecordVO> purchaseRecords(Long userId, Integer pageNum, Integer pageSize) {
        requireUserExists(userId);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize == null ? 10 : pageSize);
        Page<VipPurchaseRecord> page = new Page<>(validPageNum, validPageSize);
        Page<VipPurchaseRecord> result = vipPurchaseRecordMapper.selectPage(page,
                Wrappers.lambdaQuery(VipPurchaseRecord.class)
                        .eq(VipPurchaseRecord::getUserId, userId)
                        .eq(VipPurchaseRecord::getDeleteState, (byte) 0)
                        .orderByDesc(VipPurchaseRecord::getCreateTime)
                        .orderByDesc(VipPurchaseRecord::getId));
        List<VipPurchaseRecordVO> records = result.getRecords().stream()
                .map(VipPurchaseRecordConverter::toVO)
                .toList();
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    private void requireUserExists(Long userId) {
        Boolean exists = userInternalFeignClient.existsById(userId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
    }

    private List<VipPlanVO> buildPlans(int curTier, boolean active, boolean firstPurchaseEligible) {
        List<VipPlanVO> list = new ArrayList<>();
        list.add(planFree(curTier, active));
        list.add(planPro(curTier, active, firstPurchaseEligible));
        list.add(planMax(curTier, active, firstPurchaseEligible));
        return list;
    }

    private VipPlanVO planFree(int curTier, boolean active) {
        VipPlanVO p = new VipPlanVO();
        p.setTier(Constant.VIP_TIER_FREE);
        p.setCode("free");
        p.setName("免费");
        p.setSubtitle("基础体验，免费使用");
        p.setPricePoints(0);
        p.setDurationDays(0);
        p.setFeatured(false);
        p.setOriginalPrice(BigDecimal.ZERO);
        p.setFirstMonthPrice(BigDecimal.ZERO);
        p.setFirstPurchaseEligible(false);
        p.setFeatures(List.of(
                feat("通用额度6元/月"),
                feat("Wan 15张/月"),
                feat("更多免费权益......")));
        applyPlanQuota(p, new BigDecimal("6.0"), 15);
        if (!active || curTier == 0) {
            p.setButtonState("current");
            p.setButtonLabel("当前方案");
        } else {
            p.setButtonState("owned");
            p.setButtonLabel("已包含更高档位");
        }
        return p;
    }

    private VipPlanVO planPro(int curTier, boolean active, boolean firstPurchaseEligible) {
        VipPlanVO p = new VipPlanVO();
        p.setTier(Constant.VIP_TIER_PRO);
        p.setCode("pro");
        p.setName("PRO");
        p.setSubtitle("进阶创作者");
        p.setBadge("最受欢迎");
        p.setPricePoints(null);
        p.setDurationDays(30);
        p.setFeatured(true);
        p.setFeatures(List.of(
                feat("通用额度10.9元/月"),
                feat("Wan 20张/月"),
                feat("更多会员隐藏福利......")));
        applyPlanPricing(p, new BigDecimal("9.9"), new BigDecimal("3.9"), firstPurchaseEligible);
        applyPlanQuota(p, new BigDecimal("10.9"), 20);
        applyPlanButton(p, curTier, active, 1);
        return p;
    }

    private VipPlanVO planMax(int curTier, boolean active, boolean firstPurchaseEligible) {
        VipPlanVO p = new VipPlanVO();
        p.setTier(Constant.VIP_TIER_MAX);
        p.setCode("max");
        p.setName("MAX");
        p.setSubtitle("重度创作者专属");
        p.setBadge("顶配体验");
        p.setPricePoints(null);
        p.setDurationDays(30);
        p.setFeatured(false);
        p.setFeatures(List.of(
                feat("通用额度20.9元/月"),
                feat("Wan 50张/月"),
                feat("更多会员隐藏福利......")));
        applyPlanPricing(p, new BigDecimal("15.9"), new BigDecimal("6.9"), firstPurchaseEligible);
        applyPlanQuota(p, new BigDecimal("20.9"), 50);
        applyPlanButton(p, curTier, active, 2);
        return p;
    }

    private void applyPlanPricing(VipPlanVO plan, BigDecimal original, BigDecimal firstMonth,
                                  boolean firstPurchaseEligible) {
        plan.setOriginalPrice(original);
        plan.setFirstMonthPrice(firstMonth);
        plan.setFirstPurchaseEligible(firstPurchaseEligible);
    }

    private void applyPlanQuota(VipPlanVO plan, BigDecimal qwenBudget, int wanLimit) {
        plan.setQwenBudgetMicros(qwenBudget.multiply(BigDecimal.valueOf(1_000_000)).longValue());
        plan.setWanImageLimit(wanLimit);
    }

    private void applyPlanButton(VipPlanVO p, int curTier, boolean active, int planTier) {
        if (active && curTier >= planTier) {
            p.setButtonState("owned");
            p.setButtonLabel("已经拥有");
            return;
        }
        p.setButtonState("subscribe");
        p.setButtonLabel("立即开通");
    }

    private VipPlanFeatureVO feat(String text) {
        VipPlanFeatureVO f = new VipPlanFeatureVO();
        f.setText(text);
        f.setEnabled(true);
        return f;
    }

    private VipQuotaPanelVO buildQuotaPanel(Long userId, Byte tier, UserVipSubscription subscription) {
        VipQuotaPanelVO panel = new VipQuotaPanelVO();
        panel.setVipTier(tier);
        panel.setTierLabel(Constant.VIP_TIER_MAX.equals(tier) ? "MAX"
                : (Constant.VIP_TIER_PRO.equals(tier) ? "PRO" : "免费"));

        PeriodWindow window = resolvePeriod(subscription, tier);
        panel.setPeriodStart(window.start);
        panel.setPeriodEnd(window.end);

        AiUsageDailyBucketsVO usage = loadUsageSnapshot(userId, window.start, window.end);
        int totalCalls = usage.getTotalCalls() == null ? 0 : usage.getTotalCalls();
        panel.setTotalCalls(totalCalls);
        // 上限按当前生效档位取，与 AI 域判定同口径。
        // 不能用 base_quota_tier：体验卡期间它可能还停在免费档，会出现
        // 「面板显示免费额度、实际按 PRO 放行」。
        long qwenBudgetMicros = Constant.VIP_TIER_MAX.equals(tier) ? 20_900_000L
                : (Constant.VIP_TIER_PRO.equals(tier) ? 10_900_000L : 6_000_000L);
        int wanLimit = Constant.VIP_TIER_MAX.equals(tier) ? 50
                : (Constant.VIP_TIER_PRO.equals(tier) ? 20 : 15);
        long qwenUsed = Math.max(0L, nvl(usage.getQwenCostMicros()));
        panel.setQwenBudgetMicros(qwenBudgetMicros);
        panel.setQwenUsedMicros(qwenUsed);
        panel.setQwenRemainingMicros(Math.max(0L, qwenBudgetMicros - qwenUsed));
        int wanUsed = Math.max(0, usage.getWanImageUsed() == null ? 0 : usage.getWanImageUsed());
        panel.setWanImageLimit(BigDecimal.valueOf(wanLimit));
        panel.setWanImageUsed(BigDecimal.valueOf(wanUsed));
        panel.setWanImageRemaining(BigDecimal.valueOf(Math.max(0, wanLimit - wanUsed)));
        return panel;
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal positive(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private AiUsageDailyBucketsVO loadUsageSnapshot(Long userId, Date start, Date end) {
        AiUsageDailyBucketsVO snapshot = aiUsageInternalFeignClient.usageSnapshot(
                userId,
                start == null ? 0L : start.getTime(),
                end == null ? System.currentTimeMillis() : end.getTime()
        );
        return snapshot == null ? new AiUsageDailyBucketsVO() : snapshot;
    }

    private PeriodWindow resolvePeriod(UserVipSubscription subscription, Byte effectiveTier) {
        Date now = new Date();
        if (subscription != null && subscription.getQuotaPeriodStart() != null
                && subscription.getQuotaPeriodEnd() != null
                && subscription.getQuotaPeriodEnd().after(now)) {
            PeriodWindow stored = new PeriodWindow();
            stored.start = subscription.getQuotaPeriodStart();
            stored.end = subscription.getQuotaPeriodEnd();
            stored.baseTier = subscription.getBaseQuotaTier() == null
                    ? Constant.VIP_TIER_FREE
                    : subscription.getBaseQuotaTier();
            return stored;
        }
        if (Constant.VIP_TIER_FREE.equals(effectiveTier)) {
            ZonedDateTime current = ZonedDateTime.now(TAIPEI);
            PeriodWindow free = new PeriodWindow();
            free.start = Date.from(current.withDayOfMonth(1).toLocalDate().atStartOfDay(TAIPEI).toInstant());
            free.end = Date.from(current.plusMonths(1).withDayOfMonth(1).toLocalDate()
                    .atStartOfDay(TAIPEI).toInstant());
            free.baseTier = Constant.VIP_TIER_FREE;
            return free;
        }
        Date end = subscription == null ? null : subscription.getVipExpireAt();
        if (end == null) {
            ZonedDateTime z = ZonedDateTime.now(TAIPEI);
            end = Date.from(z.plusDays(30).toInstant());
        }
        ZonedDateTime endZ = end.toInstant().atZone(TAIPEI);
        ZonedDateTime startZ = endZ.minusDays(30);
        if (startZ.toInstant().isAfter(now.toInstant())) {
            startZ = ZonedDateTime.now(TAIPEI).minusDays(30);
        }
        PeriodWindow w = new PeriodWindow();
        w.start = Date.from(startZ.toInstant());
        w.end = end;
        w.baseTier = effectiveTier;
        return w;
    }

    private static class PeriodWindow {
        Date start;
        Date end;
        Byte baseTier;
    }
}
