package org.example.forumdemo.service.impl.vip;

import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.impl.user.UserDerivedCacheInvalidator;
import org.example.forumdemo.service.interfaces.vip.VipEntitlementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

@Service
public class VipEntitlementServiceImpl implements VipEntitlementService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserDerivedCacheInvalidator userDerivedCacheInvalidator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Date extendVipDays(User lockedUser, Byte tier, int days) {
        if (lockedUser == null || days <= 0) {
            return lockedUser != null ? lockedUser.getVipExpireAt() : null;
        }
        Byte grantTier = resolveGrantTier(lockedUser, tier);
        Date newExpire = computeNewExpire(lockedUser, days);
        int affected = userMapper.updateVipSubscription(lockedUser.getId(), grantTier, newExpire);
        if (affected != 1) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
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

    private Byte resolveGrantTier(User user, Byte requestedTier) {
        Byte tier = requestedTier != null ? requestedTier : Constant.VIP_TIER_PRO;
        if (!vipActive(user)) {
            return tier;
        }
        Byte cur = user.getVipTier() == null ? Constant.VIP_TIER_FREE : user.getVipTier();
        if (cur != null && cur > tier) {
            return cur;
        }
        return tier;
    }

    private Date computeNewExpire(User user, int days) {
        Date exp = user.getVipExpireAt();
        Date now = new Date();
        ZonedDateTime base = ZonedDateTime.now(SHANGHAI);
        if (vipActive(user) && exp != null && exp.after(now)) {
            base = exp.toInstant().atZone(SHANGHAI);
        }
        return Date.from(base.plusDays(days).toInstant());
    }

    private boolean vipActive(User user) {
        Byte tier = user.getVipTier();
        if (tier == null || tier == 0) {
            return false;
        }
        Date exp = user.getVipExpireAt();
        if (exp == null) {
            return true;
        }
        return exp.after(new Date());
    }
}
