package org.pluchon.forum.payment;

import lombok.Data;

// 退款结果
@Data
public class PaymentRefundResult {

    private boolean accepted;
    private String failReason;
    private String channelRefundNo;
}
