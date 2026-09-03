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

    // new 新购 / renew 续费 / upgrade 升级
    private String orderKind;

    private String orderKindLabel;
    private String paymentChannel;
    private Date periodStart;
    private Date periodEnd;
    private Date paidAt;
    private Date createTime;
}
