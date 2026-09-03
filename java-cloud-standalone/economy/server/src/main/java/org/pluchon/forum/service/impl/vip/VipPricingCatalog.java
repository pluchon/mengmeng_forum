package org.pluchon.forum.service.impl.vip;

import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.enums.VipPricePlan;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 会员定价与配额的唯一出处。
// 金额一律服务端定价：下单、算差价、渲染方案卡都读这里，前端传来的价格一个字不信。
public final class VipPricingCatalog {

    // 一个计费周期的天数，升级差价按剩余比例折算也用它
    public static final int PERIOD_DAYS = 30;

    // 订单最低收款额，折算下来不足一分钱时兜底
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    private static final BigDecimal PRO_ORIGINAL = new BigDecimal("9.9");
    private static final BigDecimal PRO_FIRST = new BigDecimal("3.9");
    private static final BigDecimal MAX_ORIGINAL = new BigDecimal("15.9");
    private static final BigDecimal MAX_FIRST = new BigDecimal("6.9");

    private static final BigDecimal FREE_QWEN_BUDGET = new BigDecimal("6.0");
    private static final BigDecimal PRO_QWEN_BUDGET = new BigDecimal("10.9");
    private static final BigDecimal MAX_QWEN_BUDGET = new BigDecimal("20.9");

    private static final int FREE_WAN_LIMIT = 15;
    private static final int PRO_WAN_LIMIT = 20;
    private static final int MAX_WAN_LIMIT = 50;

    private VipPricingCatalog() {
    }

    // 档位原价
    public static BigDecimal originalPrice(Byte tier) {
        if (Constant.VIP_TIER_MAX.equals(tier)) {
            return MAX_ORIGINAL;
        }
        if (Constant.VIP_TIER_PRO.equals(tier)) {
            return PRO_ORIGINAL;
        }
        return BigDecimal.ZERO;
    }

    // 档位首购价
    public static BigDecimal firstMonthPrice(Byte tier) {
        if (Constant.VIP_TIER_MAX.equals(tier)) {
            return MAX_FIRST;
        }
        if (Constant.VIP_TIER_PRO.equals(tier)) {
            return PRO_FIRST;
        }
        return BigDecimal.ZERO;
    }

    // 按定价体系取该档位的实付价
    public static BigDecimal price(Byte tier, VipPricePlan pricePlan) {
        return pricePlan == VipPricePlan.FIRST_PURCHASE ? firstMonthPrice(tier) : originalPrice(tier);
    }

    /**
     * PRO 升 MAX 的补差价。
     *
     * <p>差额取自**用户当初买 PRO 所用的那套定价体系**：首购体系 3 元、原价体系 6 元。
     * 再按剩余天数占一个周期的比例折算——只剩两天却收满一个月的差价，
     * 等于让用户为两天的 MAX 付三十天的钱。
     *
     * <p>不变量：刚买就升级（剩余比例为 1）时总付出应等于直接买 MAX，
     * 即 3.9+3=6.9、9.9+6=15.9。剩余比例小于 1 时不成立是对的，
     * 已经消费掉的那段本来就按 PRO 计价，不退也不补。
     */
    public static BigDecimal upgradeAmount(VipPricePlan pricePlan, long remainingMillis) {
        BigDecimal diff = price(Constant.VIP_TIER_MAX, pricePlan)
                .subtract(price(Constant.VIP_TIER_PRO, pricePlan));
        long periodMillis = PERIOD_DAYS * 24L * 60L * 60L * 1000L;
        long remaining = Math.max(0L, Math.min(remainingMillis, periodMillis));
        BigDecimal ratio = BigDecimal.valueOf(remaining)
                .divide(BigDecimal.valueOf(periodMillis), 10, RoundingMode.HALF_UP);
        BigDecimal amount = diff.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        return amount.compareTo(MIN_AMOUNT) < 0 ? MIN_AMOUNT : amount;
    }

    // 通用额度上限，与 AI 域的放行口径同数
    public static BigDecimal qwenBudget(Byte tier) {
        if (Constant.VIP_TIER_MAX.equals(tier)) {
            return MAX_QWEN_BUDGET;
        }
        return Constant.VIP_TIER_PRO.equals(tier) ? PRO_QWEN_BUDGET : FREE_QWEN_BUDGET;
    }

    // 生图张数上限
    public static int wanImageLimit(Byte tier) {
        if (Constant.VIP_TIER_MAX.equals(tier)) {
            return MAX_WAN_LIMIT;
        }
        return Constant.VIP_TIER_PRO.equals(tier) ? PRO_WAN_LIMIT : FREE_WAN_LIMIT;
    }

    // 只有 PRO 与 MAX 可购买，免费档不是商品
    public static Byte requirePurchasableTier(Byte tier) {
        if (Constant.VIP_TIER_PRO.equals(tier) || Constant.VIP_TIER_MAX.equals(tier)) {
            return tier;
        }
        throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
    }
}
