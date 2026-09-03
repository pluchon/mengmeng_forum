package org.pluchon.forum.payment;

import lombok.Data;

import java.math.BigDecimal;

// 退款入参
@Data
public class PaymentRefundCommand {

    private String orderNo;
    private String channelTradeNo;
    private BigDecimal amount;
    private String reason;
}
