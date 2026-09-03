package org.pluchon.forum.converter;

import org.pluchon.forum.entity.db.VipPurchaseRecord;
import org.pluchon.forum.entity.enums.VipOrderKind;
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
        vo.setTierLabel(VipOrderConverter.tierLabel(source.getVipTier()));
        vo.setPaidAmount(source.getPaidAmount());
        vo.setPaymentOrderNo(source.getPaymentOrderNo());
        VipPaymentState paymentState = VipPaymentState.fromCode(source.getPaymentState());
        vo.setPaymentState(paymentState.getCode());
        vo.setPaymentStateLabel(paymentState.getLabel());
        VipOrderKind kind = VipOrderKind.fromCode(source.getOrderKind());
        vo.setOrderKind(kind.getCode());
        vo.setOrderKindLabel(kind.getLabel());
        vo.setPaymentChannel(source.getPaymentChannel());
        vo.setPeriodStart(source.getPeriodStart());
        vo.setPeriodEnd(source.getPeriodEnd());
        vo.setPaidAt(source.getPaidAt());
        vo.setCreateTime(source.getCreateTime());
        return vo;
    }
}
