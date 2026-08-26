package org.pluchon.forum.converter;

import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.entity.db.VipPurchaseRecord;
import org.pluchon.forum.entity.enums.VipPaymentState;
import org.pluchon.forum.entity.vo.vip.VipPurchaseRecordVO;

// 会员购买记录转换器
public final class VipPurchaseRecordConverter {

    private VipPurchaseRecordConverter() {
    }

    public static VipPurchaseRecordVO toVO(VipPurchaseRecord source) {
        VipPurchaseRecordVO vo = new VipPurchaseRecordVO();
        vo.setId(source.getId());
        vo.setVipTier(source.getVipTier());
        vo.setTierLabel(Constant.VIP_TIER_MAX.equals(source.getVipTier()) ? "MAX" : "PRO");
        vo.setPaidAmount(source.getPaidAmount());
        vo.setPaymentOrderNo(source.getPaymentOrderNo());
        VipPaymentState paymentState = VipPaymentState.fromCode(source.getPaymentState());
        vo.setPaymentState(paymentState.getCode());
        vo.setPaymentStateLabel(paymentState.getLabel());
        vo.setPeriodStart(source.getPeriodStart());
        vo.setPeriodEnd(source.getPeriodEnd());
        vo.setCreateTime(source.getCreateTime());
        return vo;
    }
}
