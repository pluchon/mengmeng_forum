package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 萌星辉流水，只增不改
@Data
@TableName("starlight_log")
public class StarlightLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    // 变动量：正数发放，负数消耗
    private Integer delta;

    private Integer balanceAfter;

    // 来源：1抽奖获得 2商城兑换
    private Byte sourceType;

    private Long relatedId;

    private String idempotencyKey;

    private String remark;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
