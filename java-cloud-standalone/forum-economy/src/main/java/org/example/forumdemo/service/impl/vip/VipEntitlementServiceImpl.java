package org.example.forumdemo.service.impl.vip;

import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.UserVipSubscription;
import org.example.forumdemo.mapper.UserVipSubscriptionMapper;
import org.example.forumdemo.service.impl.user.UserDerivedCacheInvalidator;
import org.example.forumdemo.service.interfaces.vip.VipEntitlementService;
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

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

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
    public Date extendVipDays(Long userId, Byte tier, int days) {
        if (userId == null || days <= 0) {
            UserVipSubscription current = getSubscription(userId);
            return current != null ? current.getVipExpireAt() : null;
        }
        UserVipSubscription sub = ensureSubscriptionForUpdate(userId);
        Byte grantTier = resolveGrantTier(sub, tier);
        Date newExpire = computeNewExpire(sub, days);
        int affected = userVipSubscriptionMapper.updateSubscription(userId, grantTier, newExpire);
        if (affected != 1) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        userDerivedCacheInvalidator.invalidateUserCaches(userId);
        return newExpire;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Date subscribeTier(Long userId, Byte tier, int days) {
        return extendVipDays(userId, tier, days);
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

    private Date computeNewExpire(UserVipSubscription sub, int days) {
        Date exp = sub.getVipExpireAt();
        Date now = new Date();
        ZonedDateTime base = ZonedDateTime.now(SHANGHAI);
        if (vipActive(sub) && exp != null && exp.after(now)) {
            base = exp.toInstant().atZone(SHANGHAI);
        }
        return Date.from(base.plusDays(days).toInstant());
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
