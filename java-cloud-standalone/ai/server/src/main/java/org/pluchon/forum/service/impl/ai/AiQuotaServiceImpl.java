package org.pluchon.forum.service.impl.ai;

import jakarta.annotation.Resource;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.mapper.AiUsageDailyMapper;
import org.pluchon.forum.mapper.ForumAiQuotaPeriodUsageMapper;
import org.pluchon.forum.mapper.ForumAiUsageLogMapper;
import org.pluchon.forum.service.interfaces.ai.AiQuotaService;
import org.pluchon.forum.service.security.AiUserContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.time.ZonedDateTime;
import java.math.BigDecimal;

@Service
public class AiQuotaServiceImpl implements AiQuotaService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final long QWEN_RESERVATION_MICROS = 10_000L;

    @Resource
    private AiUsageDailyMapper aiUsageDailyMapper;

    @Resource
    private ForumAiUsageLogMapper forumAiUsageLogMapper;

    @Resource
    private ForumAiQuotaPeriodUsageMapper forumAiQuotaPeriodUsageMapper;

    private LocalDate today() {
        return LocalDate.now(TAIPEI);
    }

    // VIP 有效：tier>0，未填到期视为长期，或到期晚于当前
    private boolean vipActive(AiUserContext user) {
        return user != null && user.isVipActive();
    }

    private boolean isProOrMax(AiUserContext user) {
        return vipActive(user) && (Constant.VIP_TIER_PRO.equals(user.getVipTier())
                || Constant.VIP_TIER_MAX.equals(user.getVipTier()));
    }

    private boolean isMax(AiUserContext user) {
        return vipActive(user) && Constant.VIP_TIER_MAX.equals(user.getVipTier());
    }

    private void ensureRow(Long userId, LocalDate d) {
        aiUsageDailyMapper.ensureUsageRow(userId, d);
    }

    @Override
    public void consumeQwenFlash(AiUserContext user) {
        ensureTextBudget(user);
    }

    @Override
    public void consumeAdvancedLlm(AiUserContext user) {
        ensureTextBudget(user);
    }

    @Override
    // 判定口径与文本一致：都读周期表的已用 + 预占。
    // 不能从 usage_log 实时聚合张数——日志是不可变审计流水，聚合出来的用量无法被额度重置卡清零。
    public void consumeImageNormal(AiUserContext user) {
        PeriodWindow window = periodWindow(user);
        int cap = isMax(user) ? 50 : (isProOrMax(user) ? 20 : 15);
        ensurePeriodRow(user, window);
        if (forumAiQuotaPeriodUsageMapper.reserveWan(user.getId(), window.key, cap) != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_QUOTA_EXCEEDED));
        }
    }


    @Override
    public void recordCoverHint(AiUserContext user) {
        LocalDate d = today();
        ensureRow(user.getId(), d);
        aiUsageDailyMapper.incrementCoverHint(user.getId(), d);
    }

    @Override
    public void releaseQwenFlash(AiUserContext user) {
        releaseQwenReservation(user);
    }

    @Override
    public void releaseAdvancedLlm(AiUserContext user) {
        releaseQwenReservation(user);
    }

    @Override
    public void releaseImageNormal(AiUserContext user) {
        PeriodWindow window = periodWindow(user);
        forumAiQuotaPeriodUsageMapper.releaseWan(user.getId(), window.key);
    }

    @Override
    public void settleUsage(AiUserContext user, boolean qwenReserved, BigDecimal qwenCost,
                            int wanImageCount) {
        PeriodWindow window = periodWindow(user);
        ensurePeriodRow(user, window);
        long actualMicros = qwenCost == null ? 0L
                : Math.max(0L, qwenCost.multiply(BigDecimal.valueOf(1_000_000)).longValue());
        forumAiQuotaPeriodUsageMapper.settle(
                user.getId(),
                window.key,
                qwenReserved ? QWEN_RESERVATION_MICROS : 0L,
                actualMicros,
                Math.max(0, wanImageCount));
    }

    private void ensureTextBudget(AiUserContext user) {
        PeriodWindow window = periodWindow(user);
        long limitMicros = isMax(user) ? 20_900_000L : (isProOrMax(user) ? 10_900_000L : 6_000_000L);
        ensurePeriodRow(user, window);
        // 原子预占：已预占 + 已用量 + 本次预占 不得超过周期上限，避免并发超卖
        if (forumAiQuotaPeriodUsageMapper.reserveQwen(
                user.getId(), window.key, QWEN_RESERVATION_MICROS, limitMicros) != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_QUOTA_EXCEEDED));
        }
    }

    private void releaseQwenReservation(AiUserContext user) {
        PeriodWindow window = periodWindow(user);
        forumAiQuotaPeriodUsageMapper.releaseQwen(
                user.getId(), window.key, QWEN_RESERVATION_MICROS);
    }

    private void ensurePeriodRow(AiUserContext user, PeriodWindow window) {
        forumAiQuotaPeriodUsageMapper.ensurePeriod(user.getId(), window.key);
    }

    // 周期锚点以 economy 域的 user_vip_subscription.quota_period_* 为准。
    // 不能从 vip_expire_at 反推：续期只延长到期时间，反推会让周期键随之漂移，
    // 每次续期都换出一个计数全零的新桶，等于白送一次额度重置。
    private PeriodWindow periodWindow(AiUserContext user) {
        PeriodWindow window = new PeriodWindow();
        if (user != null && user.getQuotaPeriodStart() != null && user.getQuotaPeriodEnd() != null) {
            window.start = user.getQuotaPeriodStart();
            window.end = user.getQuotaPeriodEnd();
            window.key = periodKey(window.start, window.end);
            return window;
        }
        // 兜底：拿不到锚点时按自然月，避免判定链路直接失败
        ZonedDateTime now = ZonedDateTime.now(TAIPEI);
        window.start = Date.from(now.withDayOfMonth(1).toLocalDate().atStartOfDay(TAIPEI).toInstant());
        window.end = Date.from(now.plusMonths(1).withDayOfMonth(1).toLocalDate()
                .atStartOfDay(TAIPEI).toInstant());
        window.key = periodKey(window.start, window.end);
        return window;
    }

    private static String periodKey(Date start, Date end) {
        return start.toInstant().atZone(TAIPEI).toLocalDate()
                + "_" + end.toInstant().atZone(TAIPEI).toLocalDate();
    }

    private static class PeriodWindow {
        private Date start;
        private Date end;
        private String key;
    }
}
