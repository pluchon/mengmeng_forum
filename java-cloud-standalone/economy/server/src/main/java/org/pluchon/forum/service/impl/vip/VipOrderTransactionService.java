package org.pluchon.forum.service.impl.vip;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.VipPurchaseRecord;
import org.pluchon.forum.entity.enums.VipOrderKind;
import org.pluchon.forum.entity.enums.VipPaymentState;
import org.pluchon.forum.mapper.VipPurchaseRecordMapper;
import org.pluchon.forum.service.interfaces.vip.VipEntitlementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 订单的两段写操作。单独拆一个 Bean 是为了让事务代理真的生效：
 * 同类里方法互调走不到代理，{@code @Transactional} 会静默失效，
 * 那样"状态已改成功、发货却抛异常"就不会回滚，用户付了钱拿不到权益。
 */
@Slf4j
@Service
public class VipOrderTransactionService {

    @Autowired
    private VipPurchaseRecordMapper vipPurchaseRecordMapper;

    @Autowired
    private VipEntitlementService vipEntitlementService;

    // 关掉该用户的旧待支付单并落新单，两件事必须一起成立
    @Transactional(rollbackFor = Exception.class)
    public VipPurchaseRecord openOrder(Long userId, VipOrderQuoter.VipQuote quote,
                                       String channel, String orderNo) {
        Date now = new Date();
        vipPurchaseRecordMapper.closePendingOfUser(userId, now);

        VipPurchaseRecord order = new VipPurchaseRecord();
        order.setUserId(userId);
        order.setVipTier(quote.getTier());
        order.setPaidAmount(quote.getAmount());
        order.setPaymentOrderNo(orderNo);
        order.setPaymentChannel(channel);
        order.setPricePlan(quote.getPricePlan().getCode());
        order.setOrderKind(quote.getKind().getCode());
        order.setExpectedExpireAt(quote.getExpectedExpireAt());
        order.setPaymentState(VipPaymentState.PENDING.getCode());
        order.setCreateTime(now);
        order.setUpdateTime(now);
        order.setDeleteState((byte) 0);
        vipPurchaseRecordMapper.insert(order);
        return order;
    }

    // 渠道下单成功后回填流水号，失败不影响订单本身
    public void attachChannelTradeNo(Long id, String channelTradeNo) {
        if (id == null || channelTradeNo == null || channelTradeNo.isBlank()) {
            return;
        }
        VipPurchaseRecord update = new VipPurchaseRecord();
        update.setId(id);
        update.setChannelTradeNo(channelTradeNo);
        vipPurchaseRecordMapper.updateById(update);
    }

    /**
     * 收款与发货同一个事务。
     *
     * <p>发货与否只看条件更新的影响行数：返回 0 说明这笔单别人已经处理过，
     * 属于渠道重推，直接返回。不能写成"先查状态再改"，中间那段空隙足够发两次货。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean settlePaidOrder(String orderNo, BigDecimal callbackAmount, String channelTradeNo) {
        VipPurchaseRecord order = findByOrderNo(orderNo);
        if (order == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_VIP_ORDER_NOT_EXISTS));
        }
        // 回调金额必须与本地订单一致。前端改价、渠道串单，最后一道都拦在这里
        if (callbackAmount == null || order.getPaidAmount().compareTo(callbackAmount) != 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_VIP_ORDER_AMOUNT_MISMATCH));
        }
        Date now = new Date();
        if (vipPurchaseRecordMapper.markPaid(orderNo, now, channelTradeNo) != 1) {
            log.info("支付回调重复到达，订单已处理 orderNo={}", orderNo);
            return false;
        }
        VipEntitlementService.VipDeliveryResult delivery = vipEntitlementService.deliverPaidOrder(
                order.getUserId(),
                order.getVipTier(),
                VipOrderKind.fromCode(order.getOrderKind()),
                order.getExpectedExpireAt());
        vipPurchaseRecordMapper.updateOrderPeriod(orderNo, delivery.periodStart, delivery.periodEnd);
        log.info("会员订单发货完成 orderNo={} userId={} tier={} kind={} expireAt={}",
                orderNo, order.getUserId(), order.getVipTier(), order.getOrderKind(), delivery.vipExpireAt);
        return true;
    }

    public VipPurchaseRecord findByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        List<VipPurchaseRecord> records = vipPurchaseRecordMapper.selectList(
                Wrappers.lambdaQuery(VipPurchaseRecord.class)
                        .eq(VipPurchaseRecord::getPaymentOrderNo, orderNo)
                        .eq(VipPurchaseRecord::getDeleteState, (byte) 0)
                        .last("LIMIT 1"));
        return records.isEmpty() ? null : records.get(0);
    }
}
