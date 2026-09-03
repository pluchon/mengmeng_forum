package org.pluchon.forum.payment;

import lombok.Data;

// 渠道下单结果：前端拿 payPayload 去渲染二维码或跳转
@Data
public class PaymentPrepareResult {

    private String payPayload;
    private String channelTradeNo;
}
