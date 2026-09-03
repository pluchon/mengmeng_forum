package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

// 会员订单流水：下单即落待支付，回调成功后才发货
@Data
@TableName("vip_purchase_record")
public class VipPurchaseRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Byte vipTier;
    private BigDecimal paidAmount;
    private String paymentOrderNo;

    // 支付渠道 mock/alipay/wechat
    private String paymentChannel;

    // 渠道流水号，对账用
    private String channelTradeNo;

    // 定价体系 first_purchase/normal，升级差价靠它区分 3 元与 6 元
    private String pricePlan;

    // 订单类型 new/renew/upgrade，三者发货规则不同
    private String orderKind;

    // 下单时锁定的会员到期日，发货前比对，不一致拒绝发货
    private Date expectedExpireAt;

    private Byte paymentState;
    private Date periodStart;
    private Date periodEnd;
    private Date paidAt;
    private Date closedAt;
    private Date createTime;
    private Date updateTime;
    private Byte deleteState;
}
