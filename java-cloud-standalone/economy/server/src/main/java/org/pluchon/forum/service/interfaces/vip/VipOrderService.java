package org.pluchon.forum.service.interfaces.vip;

import org.pluchon.forum.entity.vo.vip.VipOrderVO;

import java.util.Map;

// 会员订单：下单、查单、渠道回调发货、超时关单
public interface VipOrderService {

    // 下单，落一条待支付订单并向渠道换取二维码
    VipOrderVO createOrder(Long userId, Byte tier, String payChannel);

    // 查单，前端支付完成后轮询它拿最终状态
    VipOrderVO queryOrder(Long userId, String orderNo);

    // 渠道回调入口，返回值直接写回响应体，形状由渠道决定
    String handleCallback(String channel, Map<String, String> params, String rawBody);

    // 本地模拟支付：按真实回调的形状构造一份带签名的回调，再走同一条发货链路
    VipOrderVO mockPay(Long userId, String orderNo);

    // 超时关单，返回关掉的条数
    int closeExpiredOrders();
}
