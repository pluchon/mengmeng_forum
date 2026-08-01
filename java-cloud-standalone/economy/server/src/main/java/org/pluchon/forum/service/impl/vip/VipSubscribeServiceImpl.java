package org.pluchon.forum.service.impl.vip;

import jakarta.annotation.Resource;
import org.pluchon.forum.economy.client.EconomyUserInternalFeignClient;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.UserVipSubscription;
import org.pluchon.forum.entity.dto.vip.VipSubscribeDTO;
import org.pluchon.forum.entity.vo.points.PointsWalletVO;
import org.pluchon.forum.entity.vo.vip.VipSubscribeResultVO;
import org.pluchon.forum.entity.vo.vip.VipStatusVO;
import org.pluchon.forum.service.interfaces.points.PointsService;
import org.pluchon.forum.service.interfaces.vip.VipEntitlementService;
import org.pluchon.forum.service.interfaces.vip.VipSubscribeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class VipSubscribeServiceImpl implements VipSubscribeService {

    @Resource
    private EconomyUserInternalFeignClient userInternalFeignClient;

    @Resource
    private PointsService pointsService;

    @Resource
    private VipEntitlementService vipEntitlementService;

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

    private void requireUserExists(Long userId) {
        Boolean exists = userInternalFeignClient.existsById(userId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipSubscribeResultVO subscribe(Long userId, VipSubscribeDTO dto) {
        Byte tier = dto.getTier();
        if (tier == null || (!Constant.VIP_TIER_PRO.equals(tier) && !Constant.VIP_TIER_MAX.equals(tier))) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        requireUserExists(userId);

        UserVipSubscription sub = vipEntitlementService.getSubscription(userId);
        Byte cur = sub != null && sub.getVipTier() != null ? sub.getVipTier() : Constant.VIP_TIER_FREE;
        boolean active = vipActive(sub);

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
        Date newExpire = vipEntitlementService.extendVipDays(userId, tier, 30);

        VipSubscribeResultVO vo = new VipSubscribeResultVO();
        vo.setVipTier(tier);
        vo.setVipExpireAt(newExpire);
        vo.setPointsBalance(pointsService.getWallet(userId).getBalance());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantTrialVipDays(Long userId, int days) {
        if (days <= 0) {
            return;
        }
        requireUserExists(userId);
        vipEntitlementService.extendVipDays(userId, Constant.VIP_TIER_PRO, days);
    }

    private VipSubscribeResultVO buildSubscribeResult(Long userId, Byte tier) {
        UserVipSubscription sub = vipEntitlementService.getSubscription(userId);
        PointsWalletVO wallet = pointsService.getWallet(userId);
        VipSubscribeResultVO vo = new VipSubscribeResultVO();
        vo.setVipTier(sub != null && sub.getVipTier() != null ? sub.getVipTier() : tier);
        vo.setVipExpireAt(sub != null ? sub.getVipExpireAt() : null);
        vo.setPointsBalance(wallet.getBalance());
        return vo;
    }

    @Override
    public VipStatusVO status(Long userId) {
        requireUserExists(userId);
        UserVipSubscription sub = vipEntitlementService.getSubscription(userId);
        PointsWalletVO wallet = pointsService.getWallet(userId);
        VipStatusVO vo = new VipStatusVO();
        vo.setVipTier(sub != null ? sub.getVipTier() : Constant.VIP_TIER_FREE);
        vo.setVipExpireAt(sub != null ? sub.getVipExpireAt() : null);
        vo.setPoints(wallet.getBalance());
        return vo;
    }

    private String buildSubscribeIdempotencyKey(Long userId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return "vip_sub:" + userId + ":" + requestId.trim();
    }
}
