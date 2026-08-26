package org.pluchon.forum.entity.vo.vip;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

// 会员购买记录响应
@Data
public class VipPurchaseRecordVO {

    private Long id;
    private Byte vipTier;
    private String tierLabel;
    private BigDecimal paidAmount;
    private String paymentOrderNo;
    private Byte paymentState;
    private String paymentStateLabel;
    private Date periodStart;
    private Date periodEnd;
    private Date createTime;
}
