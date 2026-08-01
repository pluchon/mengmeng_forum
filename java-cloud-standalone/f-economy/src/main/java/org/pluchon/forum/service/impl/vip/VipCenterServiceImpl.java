package org.pluchon.forum.service.impl.vip;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.pluchon.forum.api.ai.AiUsageDailyBucketsVO;
import org.pluchon.forum.cloud.feign.AiUsageInternalFeignClient;
import org.pluchon.forum.cloud.feign.UserInternalFeignClient;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.entity.vo.mascot.MascotQuotaHintVO;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.ForumVipQuotaConfig;
import org.pluchon.forum.entity.db.UserVipSubscription;
import org.pluchon.forum.entity.vo.vip.VipCenterVO;
import org.pluchon.forum.entity.vo.vip.VipPlanFeatureVO;
import org.pluchon.forum.entity.vo.vip.VipPlanVO;
import org.pluchon.forum.entity.vo.vip.VipQuotaGroupVO;
import org.pluchon.forum.entity.vo.vip.VipQuotaItemVO;
import org.pluchon.forum.entity.vo.vip.VipQuotaPanelVO;
import org.pluchon.forum.mapper.ForumVipQuotaConfigMapper;
import org.pluchon.forum.service.interfaces.points.PointsService;
import org.pluchon.forum.service.interfaces.vip.VipCenterService;
import org.pluchon.forum.service.interfaces.vip.VipEntitlementService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class VipCenterServiceImpl implements VipCenterService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private UserInternalFeignClient userInternalFeignClient;

    @Resource
    private VipEntitlementService vipEntitlementService;

    @Resource
    private PointsService pointsService;

    @Resource
    private ForumVipQuotaConfigMapper forumVipQuotaConfigMapper;

    @Resource
    private AiUsageInternalFeignClient aiUsageInternalFeignClient;

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
        vo.setPlans(buildPlans(cur, active));
        if (active && (Constant.VIP_TIER_PRO.equals(vipTier) || Constant.VIP_TIER_MAX.equals(vipTier))) {
            vo.setQuota(buildQuotaPanel(userId, vipTier, vipExpireAt));
        } else {
            VipQuotaPanelVO empty = new VipQuotaPanelVO();
            empty.setEmptyHint("开通 PRO 或 MAX 后可查看本期模型配额与用量");
            vo.setQuota(empty);
        }
        return vo;
    }

    @Override
    public MascotQuotaHintVO quotaHintForLlmRoute(Long userId, String llmRoute) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("percent", 0);
        out.put("canUsePointsPay", false);
        out.put("quotaLabel", "");
        requireUserExists(userId);
        UserVipSubscription sub = vipEntitlementService.getSubscription(userId);
        Byte vipTier = sub != null ? sub.getVipTier() : Constant.VIP_TIER_FREE;
        Date vipExpireAt = sub != null ? sub.getVipExpireAt() : null;
        if (!vipActive(sub) || (!Constant.VIP_TIER_PRO.equals(vipTier)
                && !Constant.VIP_TIER_MAX.equals(vipTier))) {
            return toQuotaHintVO(out);
        }
        String route = "qwen-deep";
        VipQuotaPanelVO panel = buildQuotaPanel(userId, vipTier, vipExpireAt);
        String modelCode = resolveModelCodeFromRoute(route);
        int percent = 0;
        String label = "";
        for (VipQuotaGroupVO g : panel.getGroups()) {
            if (g.getItems() == null) {
                continue;
            }
            for (VipQuotaItemVO item : g.getItems()) {
                if ("unlimited".equals(item.getQuotaType())) {
                    continue;
                }
                boolean match = false;
                if ("token_period".equals(item.getQuotaType()) && modelCode != null
                        && modelCode.equals(item.getModelCode())) {
                    match = true;
                }
                if ("daily_count".equals(item.getQuotaType()) && routeNeedsAdvancedDaily(route)
                        && "advanced_llm".equals(item.getQuotaKey())) {
                    match = true;
                }
                if (match) {
                    percent = item.getPercent() != null ? item.getPercent() : 0;
                    label = item.getDisplayName();
                    break;
                }
            }
            if (label != null && !label.isEmpty() && percent > 0) {
                break;
            }
        }
        if (label.isEmpty() && modelCode != null) {
            for (VipQuotaGroupVO g : panel.getGroups()) {
                for (VipQuotaItemVO item : g.getItems()) {
                    if (modelCode.equals(item.getModelCode())) {
                        percent = item.getPercent() != null ? item.getPercent() : 0;
                        label = item.getDisplayName();
                        break;
                    }
                }
            }
        }
        out.put("percent", percent);
        out.put("quotaLabel", label);
        out.put("canUsePointsPay", percent >= 95);
        return toQuotaHintVO(out);
    }

    private static MascotQuotaHintVO toQuotaHintVO(Map<String, Object> raw) {
        MascotQuotaHintVO vo = new MascotQuotaHintVO();
        Object percent = raw.get("percent");
        vo.setPercent(percent instanceof Number n ? n.intValue() : 0);
        vo.setCanUsePointsPay(Boolean.TRUE.equals(raw.get("canUsePointsPay")));
        Object label = raw.get("quotaLabel");
        vo.setQuotaLabel(label == null ? "" : String.valueOf(label));
        return vo;
    }

    private static boolean routeNeedsAdvancedDaily(String route) {
        return route.startsWith("qwen-deep");
    }

    private static String resolveModelCodeFromRoute(String route) {
        if (route == null || route.isBlank()) {
            return "qwen3.6-flash";
        }
        return switch (route) {
            case "qwen-flash" -> "qwen3.6-flash";
            case "qwen-deep" -> "qwen3.7-max";
            default -> route;
        };
    }

    @Override
    public VipQuotaPanelVO quota(Long userId) {
        requireUserExists(userId);
        UserVipSubscription sub = vipEntitlementService.getSubscription(userId);
        Byte vipTier = sub != null ? sub.getVipTier() : Constant.VIP_TIER_FREE;
        Date vipExpireAt = sub != null ? sub.getVipExpireAt() : null;
        if (!vipActive(sub)) {
            VipQuotaPanelVO empty = new VipQuotaPanelVO();
            empty.setEmptyHint("开通 PRO 或 MAX 后可查看本期模型配额与用量");
            return empty;
        }
        if (!Constant.VIP_TIER_PRO.equals(vipTier) && !Constant.VIP_TIER_MAX.equals(vipTier)) {
            VipQuotaPanelVO empty = new VipQuotaPanelVO();
            empty.setEmptyHint("当前档位暂无配额面板");
            return empty;
        }
        return buildQuotaPanel(userId, vipTier, vipExpireAt);
    }

    private void requireUserExists(Long userId) {
        Boolean exists = userInternalFeignClient.existsById(userId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
    }

    private List<VipPlanVO> buildPlans(int curTier, boolean active) {
        List<VipPlanVO> list = new ArrayList<>();
        list.add(planFree(curTier, active));
        list.add(planPro(curTier, active));
        list.add(planMax(curTier, active));
        return list;
    }

    private VipPlanVO planFree(int curTier, boolean active) {
        VipPlanVO p = new VipPlanVO();
        p.setTier(Constant.VIP_TIER_FREE);
        p.setCode("free");
        p.setName("默认");
        p.setSubtitle("基础体验，免费使用");
        p.setPricePoints(0);
        p.setDurationDays(0);
        p.setFeatured(false);
        p.setFeatures(List.of(
                feat("Qwen Flash 每日 10 次", true),
                feat("推荐配图要点", true),
                feat("高级模型写作", false),
                feat("AI 生图", false),
                feat("深度模型配额", false)));
        if (!active || curTier == 0) {
            p.setButtonState("current");
            p.setButtonLabel("当前方案");
        } else {
            p.setButtonState("owned");
            p.setButtonLabel("已包含更高档位");
        }
        return p;
    }

    private VipPlanVO planPro(int curTier, boolean active) {
        VipPlanVO p = new VipPlanVO();
        p.setTier(Constant.VIP_TIER_PRO);
        p.setCode("pro");
        p.setName("PRO");
        p.setSubtitle("进阶创作者");
        p.setBadge("最受欢迎");
        p.setPricePoints(Constant.VIP_PRICE_PRO_MONTH);
        p.setDurationDays(30);
        p.setFeatured(true);
        p.setFeatures(List.of(
                feat("Qwen 智能写作", true),
                feat("Z-Image Turbo 与 GPT Image 2 生图", true),
                feat("Qwen 深度写作每日 50 次", true),
                feat("AI 生图每日 25 次", true)));
        applyPlanButton(p, curTier, active, 1);
        return p;
    }

    private VipPlanVO planMax(int curTier, boolean active) {
        VipPlanVO p = new VipPlanVO();
        p.setTier(Constant.VIP_TIER_MAX);
        p.setCode("max");
        p.setName("MAX");
        p.setSubtitle("重度创作者专属");
        p.setBadge("顶配体验");
        p.setPricePoints(Constant.VIP_PRICE_MAX_MONTH);
        p.setDurationDays(30);
        p.setFeatured(false);
        p.setFeatures(List.of(
                feat("Qwen 智能写作", true),
                feat("Z-Image Turbo 与 GPT Image 2 生图", true),
                feat("Qwen 深度写作每日 300 次", true),
                feat("AI 生图每日 100 次", true)));
        applyPlanButton(p, curTier, active, 2);
        return p;
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

    private VipPlanFeatureVO feat(String text, boolean enabled) {
        VipPlanFeatureVO f = new VipPlanFeatureVO();
        f.setText(text);
        f.setEnabled(enabled);
        return f;
    }

    private VipQuotaPanelVO buildQuotaPanel(Long userId, Byte tier, Date vipExpireAt) {
        VipQuotaPanelVO panel = new VipQuotaPanelVO();
        panel.setVipTier(tier);
        panel.setTierLabel(Constant.VIP_TIER_MAX.equals(tier) ? "MAX" : "PRO");

        PeriodWindow window = resolvePeriod(vipExpireAt);
        panel.setPeriodStart(window.start);
        panel.setPeriodEnd(window.end);

        AiUsageDailyBucketsVO usage = loadUsageSnapshot(userId, window.start, window.end);
        Map<String, Long> tokenByModel = usage.getTokenByModel() == null
                ? Collections.emptyMap()
                : usage.getTokenByModel();
        int totalCalls = usage.getTotalCalls() == null ? 0 : usage.getTotalCalls();
        long totalTokens = tokenByModel.values().stream().mapToLong(Long::longValue).sum();
        panel.setTotalCalls(totalCalls);
        panel.setTotalTokensUsed(totalTokens);

        List<ForumVipQuotaConfig> configs = forumVipQuotaConfigMapper.selectList(
                Wrappers.lambdaQuery(ForumVipQuotaConfig.class)
                        .eq(ForumVipQuotaConfig::getVipTier, tier)
                        .eq(ForumVipQuotaConfig::getEnabled, 1)
                        .orderByAsc(ForumVipQuotaConfig::getSortOrder));

        Map<String, VipQuotaGroupVO> groupMap = new LinkedHashMap<>();
        for (ForumVipQuotaConfig cfg : configs) {
            if (isHiddenQuota(cfg)) {
                continue;
            }
            VipQuotaItemVO item = toItem(cfg, usage, tokenByModel, window);
            groupMap.computeIfAbsent(cfg.getGroupLabel(), k -> {
                VipQuotaGroupVO g = new VipQuotaGroupVO();
                g.setLabel(k);
                g.setItems(new ArrayList<>());
                return g;
            }).getItems().add(item);
        }
        panel.setGroups(new ArrayList<>(groupMap.values()));
        return panel;
    }

    private boolean isHiddenQuota(ForumVipQuotaConfig cfg) {
        if (cfg == null) {
            return true;
        }
        return "advanced_llm".equals(cfg.getQuotaKey());
    }

    private VipQuotaItemVO toItem(ForumVipQuotaConfig cfg, AiUsageDailyBucketsVO daily,
                                  Map<String, Long> tokenByModel, PeriodWindow window) {
        VipQuotaItemVO item = new VipQuotaItemVO();
        item.setQuotaKey(cfg.getQuotaKey());
        item.setDisplayName(cfg.getDisplayName());
        item.setModelCode(cfg.getModelCode());
        item.setIconProvider(cfg.getIconProvider());
        item.setQuotaType(cfg.getQuotaType());
        item.setTierTag(cfg.getTierTag());

        String type = cfg.getQuotaType();
        if ("unlimited".equals(type)) {
            item.setScopeLabel("会员期内");
            item.setUsed(0L);
            item.setLimit(null);
            item.setUnit("无限");
            item.setPercent(0);
            item.setResetHint("Qwen Flash 写作额度由服务端统一管理");
            return item;
        }
        if ("daily_count".equals(type)) {
            item.setScopeLabel("每日");
            int used = readDailyBucket(daily, cfg.getDailyBucket());
            int limit = cfg.getDailyLimit() != null ? cfg.getDailyLimit() : 0;
            item.setUsed((long) used);
            item.setLimit((long) limit);
            item.setUnit("次");
            item.setPercent(limit > 0 ? Math.min(100, (int) Math.round(used * 100.0 / limit)) : 0);
            item.setResetHint("明日 00:00 重置（自然日）");
            return item;
        }
        if ("token_period".equals(type)) {
            item.setScopeLabel("本周期");
            String model = cfg.getModelCode();
            long used = tokenByModel.getOrDefault(model, 0L);
            long limit = cfg.getTokenLimit() != null ? cfg.getTokenLimit() : 0L;
            item.setUsed(used);
            item.setLimit(limit);
            item.setUnit("tokens");
            item.setPercent(limit > 0 ? (int) Math.min(100, Math.round(used * 100.0 / limit)) : 0);
            item.setResetHint(resetHintForPeriod(window.end) + "（订阅周期）");
            return item;
        }
        item.setUsed(0L);
        item.setLimit(0L);
        item.setUnit("");
        item.setPercent(0);
        item.setResetHint("");
        return item;
    }

    private int readDailyBucket(AiUsageDailyBucketsVO daily, String bucket) {
        if (daily == null || bucket == null) {
            return 0;
        }
        return switch (bucket) {
            case "qwen_flash" -> nvl(daily.getQwenFlashUsed());
            case "advanced_llm" -> nvl(daily.getAdvancedLlmUsed());
            case "image_normal" -> nvl(daily.getImageNormalUsed());
            case "image_premium" -> nvl(daily.getImagePremiumUsed());
            case "companion_normal" -> nvl(daily.getCompanionNormalUsed());
            case "companion_premium" -> nvl(daily.getCompanionPremiumUsed());
            default -> 0;
        };
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private AiUsageDailyBucketsVO loadUsageSnapshot(Long userId, Date start, Date end) {
        AiUsageDailyBucketsVO snapshot = aiUsageInternalFeignClient.usageSnapshot(
                userId,
                start == null ? 0L : start.getTime(),
                end == null ? System.currentTimeMillis() : end.getTime()
        );
        return snapshot == null ? new AiUsageDailyBucketsVO() : snapshot;
    }

    private String resetHintForPeriod(Date periodEnd) {
        if (periodEnd == null) {
            return "周期内有效";
        }
        LocalDate end = periodEnd.toInstant().atZone(SHANGHAI).toLocalDate();
        long days = ChronoUnit.DAYS.between(LocalDate.now(SHANGHAI), end);
        if (days < 0) {
            return "已到期";
        }
        if (days == 0) {
            return "今日重置";
        }
        return "周期重置于 " + end.format(DATE_FMT);
    }

    private PeriodWindow resolvePeriod(Date vipExpireAt) {
        Date now = new Date();
        Date end = vipExpireAt;
        if (end == null) {
            ZonedDateTime z = ZonedDateTime.now(SHANGHAI);
            end = Date.from(z.plusDays(30).toInstant());
        }
        ZonedDateTime endZ = end.toInstant().atZone(SHANGHAI);
        ZonedDateTime startZ = endZ.minusDays(30);
        if (startZ.toInstant().isAfter(now.toInstant())) {
            startZ = ZonedDateTime.now(SHANGHAI).minusDays(30);
        }
        PeriodWindow w = new PeriodWindow();
        w.start = Date.from(startZ.toInstant());
        w.end = end;
        return w;
    }

    private static class PeriodWindow {
        Date start;
        Date end;
    }
}
