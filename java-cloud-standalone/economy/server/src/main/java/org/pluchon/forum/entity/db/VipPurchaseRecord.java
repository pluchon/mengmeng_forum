package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

// 会员支付成功流水
@Data
@TableName("vip_purchase_record")
public class VipPurchaseRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Byte vipTier;
    private BigDecimal paidAmount;
    private String paymentOrderNo;
    private Byte paymentState;
    private Date periodStart;
    private Date periodEnd;
    private Date createTime;
    private Date updateTime;
    private Byte deleteState;
}
