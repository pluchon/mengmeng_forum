package org.pluchon.forum.service.impl.vip;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.ForumTimeZone;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.converter.VipOrderConverter;
import org.pluchon.forum.economy.client.EconomyUserInternalFeignClient;
import org.pluchon.forum.entity.db.UserVipSubscription;
import org.pluchon.forum.entity.db.VipPurchaseRecord;
import org.pluchon.forum.entity.enums.VipPaymentState;
import org.pluchon.forum.entity.vo.vip.VipOrderVO;
import org.pluchon.forum.mapper.VipPurchaseRecordMapper;
import org.pluchon.forum.payment.MockPaymentGateway;
import org.pluchon.forum.payment.PaymentCallbackResult;
import org.pluchon.forum.payment.PaymentGateway;
import org.pluchon.forum.payment.PaymentGatewayRegistry;
import org.pluchon.forum.payment.PaymentPrepareCommand;
import org.pluchon.forum.payment.PaymentPrepareResult;
import org.pluchon.forum.service.interfaces.vip.VipEntitlementService;
import org.pluchon.forum.service.interfaces.vip.VipOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// 会员订单：状态单向 待支付 → 成功 / 关闭，没有回头路
@Slf4j
@Service
public class VipOrderServiceImpl implements VipOrderService {

    private static final ZoneId TAIPEI = ForumTimeZone.ZONE_ID;
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${forum.payment.order-timeout-minutes:30}")
    private int orderTimeoutMinutes;

    @Autowired
    private EconomyUserInternalFeignClient userInternalFeignClient;

    @Autowired
    private VipPurchaseRecordMapper vipPurchaseRecordMapper;

    @Autowired
    private VipEntitlementService vipEntitlementService;

    @Autowired
    private VipOrderQuoter vipOrderQuoter;

    @Autowired
    private VipOrderTransactionService vipOrderTransactionService;

    @Autowired
    private PaymentGatewayRegistry paymentGatewayRegistry;

    @Autowired
    private MockPaymentGateway mockPaymentGateway;

    @Override
    public VipOrderVO createOrder(Long userId, Byte tier, String payChannel) {
        requireUserExists(userId);
        Byte purchasableTier = VipPricingCatalog.requirePurchasableTier(tier);
        String channel = payChannel == null || payChannel.isBlank()
                ? MockPaymentGateway.CHANNEL
                : payChannel.trim();
        PaymentGateway gateway = paymentGatewayRegistry.require(channel);

        // 金额一律服务端算，前端传来的价格一个字不信
        VipOrderQuoter.VipQuote quote = vipOrderQuoter.quote(userId, purchasableTier);
        if (quote.isDowngrade()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_VIP_DOWNGRADE_NOT_ALLOWED));
        }

        VipPurchaseRecord order = vipOrderTransactionService.openOrder(
                userId, quote, channel, nextOrderNo(userId));

        // 渠道调用放在事务外：真渠道是一次 HTTP 往返，压在事务里会一直占着数据库连接
        PaymentPrepareCommand command = new PaymentPrepareCommand();
        command.setUserId(userId);
        command.setOrderNo(order.getPaymentOrderNo());
        command.setAmount(order.getPaidAmount());
        command.setSubject("星愿通行证 " + VipOrderConverter.tierLabel(purchasableTier)
                + " " + quote.getKind().getLabel());
        command.setExpireAt(orderExpireAt(order.getCreateTime()));
        PaymentPrepareResult prepared = gateway.createOrder(command);
        vipOrderTransactionService.attachChannelTradeNo(order.getId(), prepared.getChannelTradeNo());
        order.setChannelTradeNo(prepared.getChannelTradeNo());

        VipOrderVO vo = VipOrderConverter.toVO(order);
        vo.setPayPayload(prepared.getPayPayload());
        vo.setOrderExpireAt(orderExpireAt(order.getCreateTime()));
        return vo;
    }

    @Override
    public VipOrderVO queryOrder(Long userId, String orderNo) {
        VipPurchaseRecord order = requireOwnOrder(userId, orderNo);
        // 查单顺手关掉过期的待支付单。定时清扫要 forum.features.scheduling 打开才跑，
        // 这条路径不依赖它，超时关单的语义任何情况下都成立
        if (VipPaymentState.PENDING.getCode() == order.getPaymentState()
                && orderExpireAt(order.getCreateTime()).before(new Date())) {
            vipPurchaseRecordMapper.closeByOrderNo(orderNo, new Date());
            order = requireOwnOrder(userId, orderNo);
        }
        VipOrderVO vo = VipOrderConverter.toVO(order);
        vo.setOrderExpireAt(orderExpireAt(order.getCreateTime()));
        if (VipPaymentState.SUCCESS.getCode() == order.getPaymentState()) {
            UserVipSubscription subscription = vipEntitlementService.getSubscription(userId);
            vo.setVipExpireAt(subscription == null ? null : subscription.getVipExpireAt());
        }
        return vo;
    }

    @Override
    public String handleCallback(String channel, Map<String, String> params, String rawBody) {
        PaymentGateway gateway = paymentGatewayRegistry.require(channel);
        PaymentCallbackResult callback = gateway.verifyCallback(params, rawBody);
        if (!callback.isVerified()) {
            log.warn("支付回调验签未通过 channel={} reason={}", channel, callback.getFailReason());
            return gateway.callbackAck(false);
        }
        if (!callback.isPaidSuccess()) {
            // 渠道明确说没付成功：关单，不发货
            if (callback.getOrderNo() != null && !callback.getOrderNo().isBlank()) {
                vipPurchaseRecordMapper.closeByOrderNo(callback.getOrderNo(), new Date());
            }
            return gateway.callbackAck(true);
        }
        try {
            vipOrderTransactionService.settlePaidOrder(
                    callback.getOrderNo(), callback.getAmount(), callback.getChannelTradeNo());
        } catch (ApplicationException exception) {
            // 验签过了但业务上拒绝发货（金额对不上、会员状态已变）。
            // 事务已经整体回滚，订单仍停在待支付。回调本身收到了，
            // 仍要返回渠道要的应答，否则它会一直重推同一条
            log.error("支付回调已验签但拒绝发货 orderNo={} reason={}",
                    callback.getOrderNo(), exception.getMessage());
        }
        return gateway.callbackAck(true);
    }

    @Override
    public VipOrderVO mockPay(Long userId, String orderNo) {
        VipPurchaseRecord order = requireOwnOrder(userId, orderNo);
        if (!MockPaymentGateway.CHANNEL.equals(order.getPaymentChannel())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PAYMENT_CHANNEL_UNSUPPORTED));
        }
        // 真实环境这一步是渠道服务器发起的，本地由这里按同样的形状构造并签名，
        // 走的是同一条回调链路，验签、金额比对、幂等一个都不跳过
        Map<String, String> params = mockPaymentGateway.buildSignedCallback(
                order.getPaymentOrderNo(), order.getPaidAmount(), order.getChannelTradeNo());
        handleCallback(MockPaymentGateway.CHANNEL, params, null);
        return queryOrder(userId, orderNo);
    }

    @Override
    public int closeExpiredOrders() {
        Date now = new Date();
        Date before = Date.from(ZonedDateTime.ofInstant(now.toInstant(), TAIPEI)
                .minusMinutes(Math.max(1, orderTimeoutMinutes)).toInstant());
        return vipPurchaseRecordMapper.closeExpiredPending(before, now);
    }

    // 兜底清扫。需要 forum.features.scheduling 打开才会跑；没开也不影响正确性，
    // 下单与查单两条路径各自会把过期单关掉
    @Scheduled(fixedDelay = 300000L, initialDelay = 120000L)
    public void sweepExpiredOrders() {
        int closed = closeExpiredOrders();
        if (closed > 0) {
            log.info("会员订单超时关单 {} 笔", closed);
        }
    }

    private Date orderExpireAt(Date createTime) {
        Date base = createTime == null ? new Date() : createTime;
        return Date.from(ZonedDateTime.ofInstant(base.toInstant(), TAIPEI)
                .plusMinutes(Math.max(1, orderTimeoutMinutes)).toInstant());
    }

    private VipPurchaseRecord requireOwnOrder(Long userId, String orderNo) {
        VipPurchaseRecord order = vipOrderTransactionService.findByOrderNo(orderNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_VIP_ORDER_NOT_EXISTS));
        }
        return order;
    }

    private void requireUserExists(Long userId) {
        Boolean exists = userInternalFeignClient.existsById(userId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
    }

    private String nextOrderNo(Long userId) {
        return "V" + ZonedDateTime.now(TAIPEI).format(ORDER_NO_TIME)
                + userId
                + ThreadLocalRandom.current().nextInt(100000, 1000000);
    }
}
