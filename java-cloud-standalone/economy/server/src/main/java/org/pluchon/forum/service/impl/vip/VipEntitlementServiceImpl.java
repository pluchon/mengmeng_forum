package org.pluchon.forum.service.impl.vip;

import org.pluchon.forum.common.constant.ForumTimeZone;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.UserVipSubscription;
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
        UserVipSubscription sub = ensureSubscriptionForUpdate(userId);
        Date nowDate = new Date();
        if (sub.getQuotaPeriodStart() != null && sub.getQuotaPeriodEnd() != null
                && sub.getQuotaPeriodEnd().after(nowDate)) {
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
