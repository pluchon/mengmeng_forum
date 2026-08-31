package org.pluchon.forum.service.impl.starlight;

import org.pluchon.forum.cloud.feign.AiUsageInternalFeignClient;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.constant.ForumTimeZone;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.UserVipSubscription;
import org.pluchon.forum.service.interfaces.starlight.StarlightQuotaResetService;
import org.pluchon.forum.service.interfaces.vip.VipEntitlementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Date;

// 额度重置卡：把当前配额周期的已用量清零，重置到用户自己档位的上限。
// 周期由 economy 侧算好后传给 AI 域，避免 AI 回查会员快照造成事务内回环取锁。
@Service
public class StarlightQuotaResetServiceImpl implements StarlightQuotaResetService {

    private static final ZoneId ZONE_SH = ForumTimeZone.ZONE_ID;

    @Autowired
    private VipEntitlementService vipEntitlementService;

    @Autowired
    private AiUsageInternalFeignClient aiUsageInternalFeignClient;

    @Override
    public String applyQuotaReset(Long userId) {
        UserVipSubscription subscription = vipEntitlementService.ensureCurrentBaseQuotaPeriod(userId);
        if (subscription == null
                || subscription.getQuotaPeriodStart() == null
                || subscription.getQuotaPeriodEnd() == null) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        Date periodEnd = subscription.getQuotaPeriodEnd();
        aiUsageInternalFeignClient.resetPeriodQuota(
                userId,
                subscription.getQuotaPeriodStart().getTime(),
                periodEnd.getTime());
        Byte tier = subscription.getVipTier() == null ? Constant.VIP_TIER_FREE : subscription.getVipTier();
        String tierLabel = Constant.VIP_TIER_MAX.equals(tier) ? "MAX"
                : (Constant.VIP_TIER_PRO.equals(tier) ? "PRO" : "免费");
        return "已重置为" + tierLabel + "档本周期额度，有效至 " + formatExpireText(periodEnd);
    }

    private static String formatExpireText(Date expireAt) {
        if (expireAt == null) {
            return "—";
        }
        var local = expireAt.toInstant().atZone(ZONE_SH).toLocalDateTime();
        return String.format("%04d-%02d-%02d %02d:%02d",
                local.getYear(), local.getMonthValue(), local.getDayOfMonth(),
                local.getHour(), local.getMinute());
    }
}
