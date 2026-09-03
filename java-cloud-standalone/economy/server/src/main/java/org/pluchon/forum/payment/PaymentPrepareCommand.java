package org.pluchon.forum.payment;

import lombok.Data;

import java.math.BigDecimal;

// 向渠道下单的入参
@Data
public class PaymentPrepareCommand {

    private Long userId;
    private String orderNo;
    private BigDecimal amount;
    private String subject;

    // 订单超时关闭的绝对时间，渠道侧也按它过期
    private java.util.Date expireAt;
}
