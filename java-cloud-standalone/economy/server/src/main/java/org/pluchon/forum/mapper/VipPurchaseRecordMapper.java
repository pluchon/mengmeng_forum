package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.VipPurchaseRecord;

import java.util.Date;

@Mapper
public interface VipPurchaseRecordMapper extends BaseMapper<VipPurchaseRecord> {

    /**
     * 待支付 → 成功。发货与否只看这一条的影响行数：
     * 返回 1 表示本次调用抢到了状态流转，可以发货；返回 0 表示别人已经处理过，直接当重复回调忽略。
     *
     * <p>不能写成"先查状态再改"——渠道重推与用户手动查单会并发进来，
     * 中间那段空隙足够发两次货。
     */
    @Update("UPDATE vip_purchase_record SET payment_state = 1, paid_at = #{paidAt}, "
            + "channel_trade_no = #{channelTradeNo} "
            + "WHERE payment_order_no = #{orderNo} AND payment_state = 0 AND delete_state = 0")
    int markPaid(@Param("orderNo") String orderNo,
                 @Param("paidAt") Date paidAt,
                 @Param("channelTradeNo") String channelTradeNo);

    // 发货算出权益周期后回写
    @Update("UPDATE vip_purchase_record SET period_start = #{periodStart}, period_end = #{periodEnd} "
            + "WHERE payment_order_no = #{orderNo} AND delete_state = 0")
    int updateOrderPeriod(@Param("orderNo") String orderNo,
                          @Param("periodStart") Date periodStart,
                          @Param("periodEnd") Date periodEnd);

    // 关单，同样只对待支付生效
    @Update("UPDATE vip_purchase_record SET payment_state = 2, closed_at = #{closedAt} "
            + "WHERE payment_order_no = #{orderNo} AND payment_state = 0 AND delete_state = 0")
    int closeByOrderNo(@Param("orderNo") String orderNo, @Param("closedAt") Date closedAt);

    // 同一用户同时只留一个待支付订单，下新单前先把旧的关掉
    @Update("UPDATE vip_purchase_record SET payment_state = 2, closed_at = #{closedAt} "
            + "WHERE user_id = #{userId} AND payment_state = 0 AND delete_state = 0")
    int closePendingOfUser(@Param("userId") Long userId, @Param("closedAt") Date closedAt);

    // 超时清扫
    @Update("UPDATE vip_purchase_record SET payment_state = 2, closed_at = #{closedAt} "
            + "WHERE payment_state = 0 AND delete_state = 0 AND create_time < #{before}")
    int closeExpiredPending(@Param("before") Date before, @Param("closedAt") Date closedAt);
}
