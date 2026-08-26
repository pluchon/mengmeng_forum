package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 抽奖抵扣券流水，只增不改
@Data
@TableName("lottery_voucher_log")
public class LotteryVoucherLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    // 变动量：正数发放，负数抵扣
    private Integer delta;

    private Integer balanceAfter;

    // 来源：1任务发放 2抽奖抵扣
    private Byte sourceType;

    private Long relatedId;

    private String idempotencyKey;

    private String remark;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
