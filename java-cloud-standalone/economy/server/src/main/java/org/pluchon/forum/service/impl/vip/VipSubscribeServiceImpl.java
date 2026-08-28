package org.pluchon.forum.service.impl.vip;

import jakarta.annotation.Resource;
import org.pluchon.forum.economy.client.EconomyUserInternalFeignClient;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.UserVipSubscription;
import org.pluchon.forum.entity.vo.points.PointsWalletVO;
import org.pluchon.forum.entity.vo.vip.VipStatusVO;
import org.pluchon.forum.entity.vo.vip.VipTrialGrantResultVO;
import org.pluchon.forum.entity.db.VipQuotaBonusGrant;
import org.pluchon.forum.service.interfaces.points.PointsService;
import org.pluchon.forum.service.interfaces.vip.VipEntitlementService;
import org.pluchon.forum.service.interfaces.vip.VipSubscribeService;
import org.pluchon.forum.service.interfaces.vip.VipQuotaBonusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

// VIP 订阅：扫码支付开通；积分扣款路径已关闭
@Service
public class VipSubscribeServiceImpl implements VipSubscribeService {

    @Resource
    private EconomyUserInternalFeignClient userInternalFeignClient;

    @Resource
    private PointsService pointsService;

    @Resource
    private VipEntitlementService vipEntitlementService;

    @Resource
    private VipQuotaBonusService vipQuotaBonusService;

    private void requireUserExists(Long userId) {
        Boolean exists = userInternalFeignClient.existsById(userId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipTrialGrantResultVO grantTrialVipDays(Long userId, int days, String sourceType, String idempotencyKey) {
        return grantTrialVip(userId, Constant.VIP_TIER_PRO, days, sourceType, idempotencyKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipTrialGrantResultVO grantTrialVip(Long userId, Byte tier, int days,
                                               String sourceType, String idempotencyKey) {
        if (days <= 0) {
            return null;
        }
        requireUserExists(userId);
        UserVipSubscription current = vipEntitlementService.ensureCurrentBaseQuotaPeriod(userId);
        boolean maxActive = current != null
                && Constant.VIP_TIER_MAX.equals(current.getVipTier())
                && (current.getVipExpireAt() == null || current.getVipExpireAt().after(new Date()));
        boolean grantMax = Constant.VIP_TIER_MAX.equals(tier);
        Byte actualTier = grantMax || maxActive ? Constant.VIP_TIER_MAX : Constant.VIP_TIER_PRO;
        // MAX 用户领 PRO 卡按半价折算；MAX 卡本身是顶档，始终足额
        int actualHours = !grantMax && maxActive
                ? Math.multiplyExact(days, 12)
                : Math.multiplyExact(days, 24);
        Date newExpire = vipEntitlementService.extendVipHours(userId, actualTier, actualHours);
        VipQuotaBonusGrant bonus = vipQuotaBonusService.grantTrialBonus(
                userId, days, sourceType, idempotencyKey);

        VipTrialGrantResultVO result = new VipTrialGrantResultVO();
        result.setActualTier(actualTier);
        result.setActualDurationHours(actualHours);
        result.setVipExpireAt(newExpire);
        if (bonus != null) {
            result.setQwenBonusMicros(bonus.getQwenGrantedMicros());
            result.setWanBonusCredits(bonus.getWanGrantedCredits().stripTrailingZeros().toPlainString());
            result.setBonusExpireAt(bonus.getExpireTime());
        }
        String duration = actualHours % 24 == 0
                ? (actualHours / 24) + "天"
                : actualHours + "小时";
        result.setSummary((Constant.VIP_TIER_MAX.equals(actualTier) ? "MAX" : "PRO") + "会员延长" + duration);
        return result;
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
}
