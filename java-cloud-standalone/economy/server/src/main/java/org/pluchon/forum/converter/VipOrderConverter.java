package org.pluchon.forum.converter;

import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.entity.db.VipPurchaseRecord;
import org.pluchon.forum.entity.enums.VipOrderKind;
import org.pluchon.forum.entity.enums.VipPaymentState;
import org.pluchon.forum.entity.vo.vip.VipOrderVO;

// 会员订单转换器
public final class VipOrderConverter {

    private VipOrderConverter() {
    }

    public static String tierLabel(Byte tier) {
        if (Constant.VIP_TIER_MAX.equals(tier)) {
            return "MAX";
        }
        return Constant.VIP_TIER_PRO.equals(tier) ? "PRO" : "免费";
    }

    public static VipOrderVO toVO(VipPurchaseRecord source) {
        VipOrderVO vo = new VipOrderVO();
        vo.setOrderNo(source.getPaymentOrderNo());
        vo.setVipTier(source.getVipTier());
        vo.setTierLabel(tierLabel(source.getVipTier()));
        VipOrderKind kind = VipOrderKind.fromCode(source.getOrderKind());
        vo.setOrderKind(kind.getCode());
        vo.setOrderKindLabel(kind.getLabel());
        vo.setAmount(source.getPaidAmount());
        vo.setPayChannel(source.getPaymentChannel());
        VipPaymentState state = VipPaymentState.fromCode(source.getPaymentState());
        vo.setPaymentState(state.getCode());
        vo.setPaymentStateLabel(state.getLabel());
        vo.setCreateTime(source.getCreateTime());
        return vo;
    }
}
