package org.pluchon.forum.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 本地模拟渠道。没有真实收款，但**验签逻辑是真写的**：
 * 回调必须带上用共享密钥算出的签名才认。
 *
 * <p>这一点不能省。如果 mock 直接返回"验签通过"，
 * 上层就永远不会走到拒绝分支，将来换成真渠道时才发现整条失败路径从没跑过。
 */
@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    public static final String CHANNEL = "mock";

    @Value("${forum.payment.mock.secret:forum-mock-payment-secret}")
    private String secret;

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public PaymentPrepareResult createOrder(PaymentPrepareCommand command) {
        PaymentPrepareResult result = new PaymentPrepareResult();
        result.setChannelTradeNo("MOCK" + UUID.randomUUID().toString().replace("-", ""));
        // 真渠道这里是二维码内容或收银台地址，本地就把订单号原样带出去
        result.setPayPayload("mockpay://order/" + command.getOrderNo());
        return result;
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> params, String rawBody) {
        if (params == null || params.isEmpty()) {
            return PaymentCallbackResult.reject("回调参数为空");
        }
        if (!PaymentSignatures.verify(params, secret)) {
            log.warn("模拟支付回调验签失败 orderNo={}", params.get("orderNo"));
            return PaymentCallbackResult.reject("验签失败");
        }
        PaymentCallbackResult result = new PaymentCallbackResult();
        result.setVerified(true);
        result.setOrderNo(params.get("orderNo"));
        result.setChannelTradeNo(params.get("channelTradeNo"));
        result.setPaidSuccess("SUCCESS".equals(params.get("tradeStatus")));
        String amount = params.get("amount");
        try {
            result.setAmount(amount == null ? null : new BigDecimal(amount));
        } catch (NumberFormatException exception) {
            return PaymentCallbackResult.reject("回调金额格式非法");
        }
        return result;
    }

    @Override
    public PaymentRefundResult refund(PaymentRefundCommand command) {
        PaymentRefundResult result = new PaymentRefundResult();
        result.setAccepted(true);
        result.setChannelRefundNo("MOCKREFUND" + UUID.randomUUID().toString().replace("-", ""));
        log.info("模拟渠道退款 orderNo={} amount={} reason={}",
                command.getOrderNo(), command.getAmount(), command.getReason());
        return result;
    }

    @Override
    public String callbackAck(boolean accepted) {
        // 支付宝是纯文本 success，微信是 XML/JSON。这里保留同样的形状：应答体由渠道自己定
        return accepted ? "success" : "fail";
    }

    /**
     * 给本地"模拟支付成功"按钮用：按真实回调的形状拼一份带签名的参数。
     * 生产上这一步是渠道服务器做的，本地由 {@code /vip/order/mock-pay} 代劳。
     */
    public Map<String, String> buildSignedCallback(String orderNo, BigDecimal amount, String channelTradeNo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orderNo", orderNo);
        params.put("amount", amount.toPlainString());
        params.put("channelTradeNo", channelTradeNo == null ? "" : channelTradeNo);
        params.put("tradeStatus", "SUCCESS");
        params.put(PaymentSignatures.SIGN_FIELD, PaymentSignatures.sign(params, secret));
        return params;
    }
}
