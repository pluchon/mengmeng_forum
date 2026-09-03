package org.pluchon.forum.service.impl.vip;

import org.pluchon.forum.common.constant.ForumTimeZone;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.UserVipSubscription;
import org.pluchon.forum.entity.enums.VipOrderKind;
import org.pluchon.forum.mapper.UserVipSubscriptionMapper;
import org.pluchon.forum.service.impl.user.UserDerivedCacheInvalidator;
import org.pluchon.forum.service.interfaces.vip.VipEntitlementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

// VIP 权益写入 user_vip_subscription，不再依赖 auth 库 user 表
@Service
public class VipEntitlementServiceImpl implements VipEntitlementService {

    private static final ZoneId TAIPEI = ForumTimeZone.ZONE_ID;

    @Autowired
    private UserVipSubscriptionMapper userVipSubscriptionMapper;

    @Autowired
    private UserDerivedCacheInvalidator userDerivedCacheInvalidator;

    @Override
    public UserVipSubscription getSubscription(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        return userVipSubscriptionMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVipSubscription ensureCurrentBaseQuotaPeriod(Long userId) {
        // 先无锁读一次：这个方法挂在 /vip/internal/{id}/tier 后面，
        // 而 ai 域每一次 AI 请求的鉴权都要调它。绝大多数时候周期还没到期，
        // 什么都不用写——原来无条件 SELECT ... FOR UPDATE，等于每次 AI 调用
        // 都在 economy 上开一个事务、锁一行，纯属白锁
        UserVipSubscription cached = userVipSubscriptionMapper.selectByUserId(userId);
        Date nowDate = new Date();
        if (isPeriodValid(cached, nowDate)) {
            return cached;
        }
        // 确实要写了，这时候才加锁，并在锁内重新判一次：
        // 并发进来的另一个请求可能已经把周期滚好了
        UserVipSubscription sub = ensureSubscriptionForUpdate(userId);
        if (isPeriodValid(sub, new Date())) {
            return sub;
        }
        ZonedDateTime now = ZonedDateTime.now(TAIPEI);
        Byte baseTier = vipActive(sub) && sub.getVipTier() != null
                ? sub.getVipTier()
                : Constant.VIP_TIER_FREE;
        Date start;
        Date end;
        if (Constant.VIP_TIER_FREE.equals(baseTier)) {
            start = Date.from(now.withDayOfMonth(1).toLocalDate().atStartOfDay(TAIPEI).toInstant());
            end = Date.from(now.plusMonths(1).withDayOfMonth(1).toLocalDate()
                    .atStartOfDay(TAIPEI).toInstant());
        } else {
            start = Date.from(now.toInstant());
            end = Date.from(now.plusDays(30).toInstant());
        }
        userVipSubscriptionMapper.updateBaseQuotaPeriod(userId, baseTier, start, end);
        sub.setBaseQuotaTier(baseTier);
        sub.setQuotaPeriodStart(start);
        sub.setQuotaPeriodEnd(end);
        return sub;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Date extendVipHours(Long userId, Byte tier, int hours) {
        if (userId == null || hours <= 0) {
            UserVipSubscription current = getSubscription(userId);
            return current != null ? current.getVipExpireAt() : null;
        }
        UserVipSubscription sub = ensureSubscriptionForUpdate(userId);
        Byte grantTier = resolveGrantTier(sub, tier);
        Date newExpire = computeNewExpire(sub, hours);
        int affected = userVipSubscriptionMapper.updateSubscription(userId, grantTier, newExpire);
        if (affected != 1) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        userDerivedCacheInvalidator.invalidateUserCaches(userId);
        return newExpire;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipDeliveryResult deliverPaidOrder(Long userId, Byte tier, VipOrderKind kind, Date expectedExpireAt) {
        UserVipSubscription sub = ensureSubscriptionForUpdate(userId);
        Date now = new Date();
        boolean active = vipActive(sub);
        Byte currentTier = active && sub.getVipTier() != null ? sub.getVipTier() : Constant.VIP_TIER_FREE;

        // 下单到付款之间用户档位反而变高了（例如中途领到体验卡）。
        // 继续发货会用低档位的钱延长高档位权益，这里宁可拒收让他重新下单
        if (currentTier > tier) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_VIP_ORDER_STATE_CHANGED));
        }

        Date newExpire;
        Date periodStart;
        Date periodEnd;
        if (kind == VipOrderKind.UPGRADE) {
            // 升级差价是按下单那一刻的剩余天数算的。到期日只要变过，这笔钱就不再对应这段权益
            if (!active || !Constant.VIP_TIER_PRO.equals(currentTier)
                    || !sameSecond(sub.getVipExpireAt(), expectedExpireAt)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_VIP_ORDER_STATE_CHANGED));
            }
            newExpire = sub.getVipExpireAt();
            // 升级把配额周期整个重置，当作升级的奖励；但周期不得越过会员到期日，
            // 否则会员结束之后还留着一段按 MAX 起算的计数窗口
            periodStart = now;
            periodEnd = earlier(Date.from(ZonedDateTime.now(TAIPEI)
                    .plusDays(VipPricingCatalog.PERIOD_DAYS).toInstant()), newExpire);
        } else {
            // 新购从今天起算，续费从原到期日往后接。
            // 续费若从今天算，等于把用户没用完的天数吞掉，投诉一告一个准
            ZonedDateTime base = active && sub.getVipExpireAt() != null && sub.getVipExpireAt().after(now)
                    ? sub.getVipExpireAt().toInstant().atZone(TAIPEI)
                    : ZonedDateTime.now(TAIPEI);
            newExpire = Date.from(base.plusDays(VipPricingCatalog.PERIOD_DAYS).toInstant());
            Date storedEnd = sub.getQuotaPeriodEnd();
            if (storedEnd != null && storedEnd.after(now)) {
                // 本期还没走完就续费：沿用当前周期，否则提前续费就成了免费的额度重置
                periodStart = sub.getQuotaPeriodStart();
                periodEnd = storedEnd;
            } else {
                periodStart = now;
                periodEnd = earlier(Date.from(ZonedDateTime.now(TAIPEI)
                        .plusDays(VipPricingCatalog.PERIOD_DAYS).toInstant()), newExpire);
            }
        }

        int affected = userVipSubscriptionMapper.updatePaidSubscription(
                userId, tier, newExpire, tier, periodStart, periodEnd);
        if (affected != 1) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        userDerivedCacheInvalidator.invalidateUserCaches(userId);

        VipDeliveryResult result = new VipDeliveryResult();
        result.vipExpireAt = newExpire;
        result.periodStart = periodStart;
        result.periodEnd = periodEnd;
        return result;
    }

    private Date earlier(Date left, Date right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.before(right) ? left : right;
    }

    // 数据库 datetime 只到秒，比对前统一截断，避免毫秒差把正常订单判成状态已变
    private boolean sameSecond(Date left, Date right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.getTime() / 1000L == right.getTime() / 1000L;
    }

    // 配额周期还在有效期内：不用滚周期，也就不用加锁
    private boolean isPeriodValid(UserVipSubscription sub, Date now) {
        return sub != null
                && sub.getQuotaPeriodStart() != null
                && sub.getQuotaPeriodEnd() != null
                && sub.getQuotaPeriodEnd().after(now);
    }

    private UserVipSubscription ensureSubscriptionForUpdate(Long userId) {
        ensureSubscriptionExists(userId);
        UserVipSubscription locked = userVipSubscriptionMapper.selectByUserIdForUpdate(userId);
        if (locked == null) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        return locked;
    }

    private void ensureSubscriptionExists(Long userId) {
        if (userVipSubscriptionMapper.selectByUserId(userId) != null) {
            return;
        }
        try {
            userVipSubscriptionMapper.insertSubscription(userId, Constant.VIP_TIER_FREE, null);
        } catch (DuplicateKeyException ignored) {
            // 并发建档
        }
    }

    private Byte resolveGrantTier(UserVipSubscription sub, Byte requestedTier) {
        Byte tier = requestedTier != null ? requestedTier : Constant.VIP_TIER_PRO;
        if (!vipActive(sub)) {
            return tier;
        }
        Byte cur = sub.getVipTier() == null ? Constant.VIP_TIER_FREE : sub.getVipTier();
        if (cur != null && cur > tier) {
            return cur;
        }
        return tier;
    }

    private Date computeNewExpire(UserVipSubscription sub, int hours) {
        Date exp = sub.getVipExpireAt();
        Date now = new Date();
        ZonedDateTime base = ZonedDateTime.now(TAIPEI);
        if (vipActive(sub) && exp != null && exp.after(now)) {
            base = exp.toInstant().atZone(TAIPEI);
        }
        return Date.from(base.plusHours(hours).toInstant());
    }

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
}
