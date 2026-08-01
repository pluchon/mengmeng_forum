package org.example.forumdemo.service.impl.vip;

import jakarta.annotation.Resource;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.vip.VipSubscribeDTO;
import org.example.forumdemo.entity.vo.vip.VipSubscribeResultVO;
import org.example.forumdemo.entity.vo.vip.VipStatusVO;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.points.PointsService;
import org.example.forumdemo.service.interfaces.vip.VipEntitlementService;
import org.example.forumdemo.service.interfaces.vip.VipSubscribeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class VipSubscribeServiceImpl implements VipSubscribeService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private PointsService pointsService;

    @Resource
    private VipEntitlementService vipEntitlementService;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipSubscribeResultVO subscribe(Long userId, VipSubscribeDTO dto) {
        Byte tier = dto.getTier();
        if (tier == null || (!Constant.VIP_TIER_PRO.equals(tier) && !Constant.VIP_TIER_MAX.equals(tier))) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null || user.getDeleteState() != null && user.getDeleteState() == 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }

        Byte cur = user.getVipTier() == null ? Constant.VIP_TIER_FREE : user.getVipTier();
        boolean active = vipActive(user);

        int curOrd = cur == null ? 0 : cur.intValue();
        int wantOrd = tier.intValue();
        if (active && curOrd > wantOrd) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_VIP_SUBSCRIBE_TIER));
        }

        int price = Constant.VIP_TIER_PRO.equals(tier) ? Constant.VIP_PRICE_PRO_MONTH : Constant.VIP_PRICE_MAX_MONTH;
        String remark = Constant.VIP_TIER_PRO.equals(tier) ? "订阅 VIP PRO（30 天）" : "订阅 VIP MAX（30 天）";
        String idempotencyKey = buildSubscribeIdempotencyKey(userId, dto.getRequestId());
        if (idempotencyKey != null && pointsService.hasIdempotencyRecord(userId, idempotencyKey)) {
            return buildSubscribeResult(userId, tier);
        }

        pointsService.deductPoints(userId, price, Constant.POINTS_SOURCE_VIP_SUBSCRIBE, userId, remark, idempotencyKey);
        Date newExpire = vipEntitlementService.extendVipDays(user, tier, 30);

        User fresh = userMapper.selectById(userId);
        VipSubscribeResultVO vo = new VipSubscribeResultVO();
        vo.setVipTier(tier);
        vo.setVipExpireAt(newExpire);
        vo.setPointsBalance(fresh != null ? fresh.getPoints() : null);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantTrialVipDays(Long userId, int days) {
        if (days <= 0) {
            return;
        }
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null || user.getDeleteState() != null && user.getDeleteState() == 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        vipEntitlementService.extendVipDays(user, Constant.VIP_TIER_PRO, days);
    }

    private VipSubscribeResultVO buildSubscribeResult(Long userId, Byte tier) {
        User fresh = userMapper.selectById(userId);
        VipSubscribeResultVO vo = new VipSubscribeResultVO();
        vo.setVipTier(fresh != null && fresh.getVipTier() != null ? fresh.getVipTier() : tier);
        vo.setVipExpireAt(fresh != null ? fresh.getVipExpireAt() : null);
        vo.setPointsBalance(fresh != null ? fresh.getPoints() : null);
        return vo;
    }

    @Override
    public VipStatusVO status(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        VipStatusVO vo = new VipStatusVO();
        vo.setVipTier(user.getVipTier());
        vo.setVipExpireAt(user.getVipExpireAt());
        vo.setPoints(user.getPoints());
        return vo;
    }

    private String buildSubscribeIdempotencyKey(Long userId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return "vip_sub:" + userId + ":" + requestId.trim();
    }
}
