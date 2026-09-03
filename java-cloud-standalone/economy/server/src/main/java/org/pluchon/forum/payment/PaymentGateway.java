package org.pluchon.forum.payment;

import java.util.Map;

/**
 * 支付渠道抽象。各家渠道的签名算法、字段名、回调应答体都不一样，
 * 但流程只有这三步：下单、验回调、退款。接入真实渠道时新增一个实现即可，
 * 订单服务里的状态流转一行都不用改。
 */
public interface PaymentGateway {

    // 渠道标识，与订单表 payment_channel 同值
    String channel();

    // 向渠道下单，拿到二维码内容或跳转地址
    PaymentPrepareResult createOrder(PaymentPrepareCommand command);

    // 验签并解析回调。只负责"这条回调是不是渠道发的"，金额与订单状态由调用方再判一次
    PaymentCallbackResult verifyCallback(Map<String, String> params, String rawBody);

    // 退款
    PaymentRefundResult refund(PaymentRefundCommand command);

    /**
     * 回调应答体。渠道拿不到约定的响应就会一直重推，
     * 所以这个形状必须由渠道自己定义，不能让上层统一返 JSON。
     */
    String callbackAck(boolean accepted);
}
