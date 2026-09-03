package org.pluchon.forum.payment;

import jakarta.annotation.PostConstruct;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 渠道注册表：按 channel() 收口，新增渠道只要多一个 Bean
@Component
public class PaymentGatewayRegistry {

    @Autowired
    private List<PaymentGateway> gateways;

    private final Map<String, PaymentGateway> byChannel = new HashMap<>();

    @PostConstruct
    public void init() {
        for (PaymentGateway gateway : gateways) {
            byChannel.put(gateway.channel(), gateway);
        }
    }

    // 取不到渠道直接拒，不要静默回退到 mock——那等于把没接通的渠道当成收款成功
    public PaymentGateway require(String channel) {
        PaymentGateway gateway = byChannel.get(channel);
        if (gateway == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PAYMENT_CHANNEL_UNSUPPORTED));
        }
        return gateway;
    }

    public boolean supports(String channel) {
        return byChannel.containsKey(channel);
    }
}
