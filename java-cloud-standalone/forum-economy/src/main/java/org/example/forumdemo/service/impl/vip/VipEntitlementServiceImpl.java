package org.example.forumdemo.service.impl.vip;

import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.db.UserVipSubscription;
import org.example.forumdemo.mapper.UserMapper;
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

// VIP 权益写入 user_vip_subscription，不再写 user.vip_*
@Service
public class VipEntitlementServiceImpl implements VipEntitlementService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserVipSubscriptionMapper userVipSubscriptionMapper;

    @Autowired
    private UserDerivedCacheInvalidator userDerivedCacheInvalidator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Date extendVipDays(User lockedUser, Byte tier, int days) {
        if (lockedUser == null || days <= 0) {
            return lockedUser != null ? lockedUser.getVipExpireAt() : null;
        }
        UserVipSubscription sub = ensureSubscription(lockedUser.getId(), lockedUser);
        Byte grantTier = resolveGrantTier(sub, tier);
        Date newExpire = computeNewExpire(sub, days);
        int affected = userVipSubscriptionMapper.updateSubscription(lockedUser.getId(), grantTier, newExpire);
        if (affected != 1) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        lockedUser.setVipTier(grantTier);
        lockedUser.setVipExpireAt(newExpire);
        userDerivedCacheInvalidator.invalidateUserCaches(lockedUser.getId());
        return newExpire;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Date subscribeTier(Long userId, Byte tier, int days) {
        User locked = userMapper.selectByIdForUpdate(userId);
        if (locked == null || locked.getDeleteState() != null && locked.getDeleteState() == 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return extendVipDays(locked, tier, days);
    }

    private UserVipSubscription ensureSubscription(Long userId, User legacyUser) {
        UserVipSubscription existing = userVipSubscriptionMapper.selectByUserId(userId);
        if (existing != null) {
            return existing;
        }
        Byte seedTier = legacyUser != null && legacyUser.getVipTier() != null
                ? legacyUser.getVipTier() : Constant.VIP_TIER_FREE;
        Date seedExpire = legacyUser != null ? legacyUser.getVipExpireAt() : null;
        try {
            userVipSubscriptionMapper.insertSubscription(userId, seedTier, seedExpire);
        } catch (DuplicateKeyException ignored) {
            // 并发建档
        }
        existing = userVipSubscriptionMapper.selectByUserId(userId);
        if (existing == null) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        return existing;
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
