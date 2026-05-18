package org.example.forumdemo.service.impl.vip;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.AiUsageDaily;
import org.example.forumdemo.entity.db.ForumVipQuotaConfig;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.vip.VipCenterVO;
import org.example.forumdemo.entity.vo.vip.VipPlanFeatureVO;
import org.example.forumdemo.entity.vo.vip.VipPlanVO;
import org.example.forumdemo.entity.vo.vip.VipQuotaGroupVO;
import org.example.forumdemo.entity.vo.vip.VipQuotaItemVO;
import org.example.forumdemo.entity.vo.vip.VipQuotaPanelVO;
import org.example.forumdemo.mapper.AiUsageDailyMapper;
import org.example.forumdemo.mapper.ForumAiUsageLogMapper;
import org.example.forumdemo.mapper.ForumVipQuotaConfigMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.vip.VipCenterService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class VipCenterServiceImpl implements VipCenterService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private UserMapper userMapper;

    @Resource
    private ForumVipQuotaConfigMapper forumVipQuotaConfigMapper;

    @Resource
    private AiUsageDailyMapper aiUsageDailyMapper;

    @Resource
    private ForumAiUsageLogMapper forumAiUsageLogMapper;

    private boolean vipActive(User u) {
        Byte tier = u.getVipTier();
        if (tier == null || tier == 0) {
            return false;
        }
        Date exp = u.getVipExpireAt();
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
        User user = requireUser(userId);
        boolean active = vipActive(user);
        int cur = tierOrder(user.getVipTier());

        VipCenterVO vo = new VipCenterVO();
        vo.setVipTier(user.getVipTier());
        vo.setVipExpireAt(user.getVipExpireAt());
        vo.setPoints(user.getPoints());
        vo.setVipActive(active);
        vo.setPlans(buildPlans(cur, active));
        if (active && (Constant.VIP_TIER_PRO.equals(user.getVipTier()) || Constant.VIP_TIER_MAX.equals(user.getVipTier()))) {
            vo.setQuota(buildQuotaPanel(user));
        } else {
            VipQuotaPanelVO empty = new VipQuotaPanelVO();
            empty.setEmptyHint("开通 PRO 或 MAX 后可查看本期模型配额与用量");
            vo.setQuota(empty);
        }
        return vo;
    }

    @Override
    public VipQuotaPanelVO quota(Long userId) {
        User user = requireUser(userId);
        if (!vipActive(user)) {
            VipQuotaPanelVO empty = new VipQuotaPanelVO();
            empty.setEmptyHint("开通 PRO 或 MAX 后可查看本期模型配额与用量");
            return empty;
        }
        if (!Constant.VIP_TIER_PRO.equals(user.getVipTier()) && !Constant.VIP_TIER_MAX.equals(user.getVipTier())) {
            VipQuotaPanelVO empty = new VipQuotaPanelVO();
            empty.setEmptyHint("当前档位暂无配额面板");
            return empty;
        }
        return buildQuotaPanel(user);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return user;
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
                feat("DeepSeek V4 Flash 写作 每日 10 次", true),
                feat("封面「推荐配图要点」单独调用", true),
                feat("高级大模型写作", false),
                feat("AI 生图", false),
                feat("深度模型 Token 配额", false),
                feat("AI 伴读", false)));
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
                feat("DeepSeek V4 Flash 会员期内不限次", true),
                feat("高级大模型写作 每日 50 次", true),
                feat("AI 生图 每日 25 次（普通 15 + 高级 10）", true),
                feat("深度模型 Token 本周期各 500K（通义/DeepSeek/Gemini）", true),
                feat("AI 伴读（即将上线）", false)));
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
                feat("DeepSeek V4 Flash 会员期内不限次", true),
                feat("高级大模型写作 每日 300 次", true),
                feat("AI 生图 每日 100 次（普通 50 + 高级 50）", true),
                feat("深度模型 Token 本周期（通义 1M / DeepSeek 2M / Gemini 2M）", true),
                feat("AI 伴读（即将上线）", false)));
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

    private VipQuotaPanelVO buildQuotaPanel(User user) {
        Byte tier = user.getVipTier();
        VipQuotaPanelVO panel = new VipQuotaPanelVO();
        panel.setVipTier(tier);
        panel.setTierLabel(Constant.VIP_TIER_MAX.equals(tier) ? "MAX" : "PRO");

        PeriodWindow window = resolvePeriod(user);
        panel.setPeriodStart(window.start);
        panel.setPeriodEnd(window.end);

        AiUsageDaily daily = loadTodayUsage(user.getId());
        Map<String, Long> tokenByModel = loadTokenByModel(user.getId(), window.start, window.end);
        int totalCalls = forumAiUsageLogMapper.countCallsBetween(user.getId(), window.start, window.end);
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
            VipQuotaItemVO item = toItem(cfg, daily, tokenByModel, window);
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

    private VipQuotaItemVO toItem(ForumVipQuotaConfig cfg, AiUsageDaily daily,
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
            item.setResetHint("DeepSeek 写作不限次（PRO/MAX）");
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

    private int readDailyBucket(AiUsageDaily daily, String bucket) {
        if (daily == null || bucket == null) {
            return 0;
        }
        return switch (bucket) {
            case "deepseek_write" -> nvl(daily.getDeepseekWriteUsed());
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

    private AiUsageDaily loadTodayUsage(Long userId) {
        LocalDate d = LocalDate.now(SHANGHAI);
        return aiUsageDailyMapper.selectOne(
                Wrappers.lambdaQuery(AiUsageDaily.class)
                        .eq(AiUsageDaily::getUserId, userId)
                        .eq(AiUsageDaily::getUsageDate, d)
                        .eq(AiUsageDaily::getDeleteState, 0)
                        .last("LIMIT 1"));
    }

    private Map<String, Long> loadTokenByModel(Long userId, Date start, Date end) {
        Map<String, Long> map = new HashMap<>();
        List<Map<String, Object>> rows = forumAiUsageLogMapper.sumTokensByModelBetween(userId, start, end);
        for (Map<String, Object> row : rows) {
            String model = Objects.toString(row.get("modelCode"), "");
            if (model.isBlank()) {
                continue;
            }
            long in = toLong(row.get("inputTokens"));
            long out = toLong(row.get("outputTokens"));
            map.put(model, in + out);
        }
        return map;
    }

    private long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
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

    private PeriodWindow resolvePeriod(User user) {
        Date now = new Date();
        Date end = user.getVipExpireAt();
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
