package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 用户 VIP 订阅，对应 user_vip_subscription economy 权威
@Data
@TableName("user_vip_subscription")
public class UserVipSubscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    // VIP档位: 0普通 1PRO 2MAX
    private Byte vipTier;

    private Date vipExpireAt;

    // 基础配额档位，不随体验卡改变
    private Byte baseQuotaTier;

    private Date quotaPeriodStart;

    private Date quotaPeriodEnd;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
