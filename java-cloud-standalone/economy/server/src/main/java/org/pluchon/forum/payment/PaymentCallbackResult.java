package org.pluchon.forum.payment;

import lombok.Data;

import java.math.BigDecimal;

// 回调验签结果。金额与订单号由调用方再和本地订单比对一次，网关不负责业务判断
@Data
public class PaymentCallbackResult {

    // 验签是否通过。false 时后面的字段一律不可信
    private boolean verified;

    private String failReason;
    private String orderNo;
    private BigDecimal amount;
    private String channelTradeNo;

    // 渠道声明的支付结果，验签通过也可能是"支付失败"
    private boolean paidSuccess;

    public static PaymentCallbackResult reject(String reason) {
        PaymentCallbackResult result = new PaymentCallbackResult();
        result.setVerified(false);
        result.setFailReason(reason);
        return result;
    }
}
