package org.pluchon.forum.service.impl.vip;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.Data;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.entity.db.UserVipSubscription;
import org.pluchon.forum.entity.db.VipPurchaseRecord;
import org.pluchon.forum.entity.enums.VipOrderKind;
import org.pluchon.forum.entity.enums.VipPaymentState;
import org.pluchon.forum.entity.enums.VipPricePlan;
import org.pluchon.forum.mapper.VipPurchaseRecordMapper;
import org.pluchon.forum.service.interfaces.vip.VipEntitlementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 报价：给定用户与目标档位，算出这笔单是新购/续费/升级、走哪套定价、该收多少钱。
 *
 * <p>下单和会员中心的方案卡读的是同一份报价。分成两处各算各的，
 * 迟早会出现"卡片写 2 元、下单收 3 元"。
 */
@Component
public class VipOrderQuoter {

    @Autowired
    private VipEntitlementService vipEntitlementService;

    @Autowired
    private VipPurchaseRecordMapper vipPurchaseRecordMapper;

    // 一次报价的全部结论
    @Data
    public static class VipQuote {

        private Byte tier;
        private VipOrderKind kind;
        private VipPricePlan pricePlan;
        private BigDecimal amount;

        // 下单时锁定的会员到期日，仅升级单有值
        private Date expectedExpireAt;

        // 折算所用的剩余天数，仅升级单有值
        private int remainingDays;

        // 首购资格：从没有过一笔成功订单
        private boolean firstPurchaseEligible;

        // 不允许下单（当前档位更高，属于降级）
        private boolean downgrade;
    }

    public VipQuote quote(Long userId, Byte tier) {
        UserVipSubscription sub = vipEntitlementService.getSubscription(userId);
        return quote(userId, tier, sub);
    }

    public VipQuote quote(Long userId, Byte tier, UserVipSubscription sub) {
        VipQuote quote = new VipQuote();
        quote.setTier(tier);
        quote.setFirstPurchaseEligible(firstPurchaseEligible(userId));

        Date now = new Date();
        boolean active = vipActive(sub, now);
        Byte currentTier = active && sub.getVipTier() != null ? sub.getVipTier() : Constant.VIP_TIER_FREE;

        if (currentTier > tier) {
            // MAX 有效期内再买 PRO：不是省钱，是把 MAX 换成 PRO 还倒贴，一律拒绝
            quote.setDowngrade(true);
            quote.setKind(VipOrderKind.RENEW);
            quote.setPricePlan(VipPricePlan.NORMAL);
            quote.setAmount(BigDecimal.ZERO);
            return quote;
        }

        if (active && Constant.VIP_TIER_PRO.equals(currentTier) && Constant.VIP_TIER_MAX.equals(tier)) {
            quote.setKind(VipOrderKind.UPGRADE);
            // 升级差额沿用当初买 PRO 那套定价：首购价买的 PRO 升 MAX 仍按首购体系补 3 元。
            // 首购优惠是为了把人拉进付费，人已经进来了，再用它惩罚升级等于劝退升级
            quote.setPricePlan(originalProPricePlan(userId, quote.isFirstPurchaseEligible()));
            // 到期日为空是"长期有效"，按满一个周期收满额差价，不能当成剩 0 天只收一分钱
            long remainingMillis = sub.getVipExpireAt() == null
                    ? VipPricingCatalog.PERIOD_DAYS * 24L * 60L * 60L * 1000L
                    : sub.getVipExpireAt().getTime() - now.getTime();
            quote.setExpectedExpireAt(sub.getVipExpireAt());
            quote.setRemainingDays(ceilDays(remainingMillis));
            quote.setAmount(VipPricingCatalog.upgradeAmount(quote.getPricePlan(), remainingMillis));
            return quote;
        }

        quote.setKind(active ? VipOrderKind.RENEW : VipOrderKind.NEW);
        quote.setPricePlan(quote.isFirstPurchaseEligible() ? VipPricePlan.FIRST_PURCHASE : VipPricePlan.NORMAL);
        quote.setAmount(VipPricingCatalog.price(tier, quote.getPricePlan()));
        return quote;
    }

    // 首购资格按成功流水判，不按当前订阅状态：退订过的人不该再享首购价
    public boolean firstPurchaseEligible(Long userId) {
        return vipPurchaseRecordMapper.selectCount(
                Wrappers.lambdaQuery(VipPurchaseRecord.class)
                        .eq(VipPurchaseRecord::getUserId, userId)
                        .eq(VipPurchaseRecord::getPaymentState, VipPaymentState.SUCCESS.getCode())
                        .eq(VipPurchaseRecord::getDeleteState, (byte) 0)) == 0;
    }

    // 找用户最近一笔成功的 PRO 订单，用它的定价体系；从没买过（例如体验卡得来的 PRO）就看首购资格
    private VipPricePlan originalProPricePlan(Long userId, boolean firstPurchaseEligible) {
        List<VipPurchaseRecord> records = vipPurchaseRecordMapper.selectList(
                Wrappers.lambdaQuery(VipPurchaseRecord.class)
                        .eq(VipPurchaseRecord::getUserId, userId)
                        .eq(VipPurchaseRecord::getVipTier, Constant.VIP_TIER_PRO)
                        .eq(VipPurchaseRecord::getPaymentState, VipPaymentState.SUCCESS.getCode())
                        .eq(VipPurchaseRecord::getDeleteState, (byte) 0)
                        .orderByDesc(VipPurchaseRecord::getId)
                        .last("LIMIT 1"));
        if (records.isEmpty()) {
            return firstPurchaseEligible ? VipPricePlan.FIRST_PURCHASE : VipPricePlan.NORMAL;
        }
        return VipPricePlan.fromCode(records.get(0).getPricePlan());
    }

    private int ceilDays(long millis) {
        if (millis <= 0L) {
            return 0;
        }
        long dayMillis = 24L * 60L * 60L * 1000L;
        return (int) ((millis + dayMillis - 1) / dayMillis);
    }

    private boolean vipActive(UserVipSubscription sub, Date now) {
        if (sub == null || sub.getVipTier() == null || sub.getVipTier() == 0) {
            return false;
        }
        return sub.getVipExpireAt() == null || sub.getVipExpireAt().after(now);
    }
}
