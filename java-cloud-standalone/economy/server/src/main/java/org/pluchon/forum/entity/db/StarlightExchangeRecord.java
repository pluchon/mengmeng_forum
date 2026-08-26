package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 萌星辉兑换记录，对应 starlight_exchange_record
@Data
@TableName("starlight_exchange_record")
public class StarlightExchangeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long itemId;

    private String itemName;

    private Integer pricePaid;

    private String rewardType;

    private Integer rewardValue;

    private String idempotencyKey;

    // 使用状态：0 未使用 1 已使用 兑换仅入背包，使用时才发放奖励
    private Integer useStatus;

    private Date useTime;

    // 实际发放档位：1PRO 2MAX
    private Byte actualGrantTier;

    // 实际延长小时数，MAX 使用 PRO 卡时按半天折算
    private Integer actualDurationHours;

    private String grantSummary;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
