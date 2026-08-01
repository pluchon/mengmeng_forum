package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 积分钱包流水表实体, 对应 points_log. 任何 user.points 变动都要在同一事务里写一条.
 */
@Data
@TableName("points_log")
@Schema(description = "积分钱包流水实体")
public class PointsLog {

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "本次变动量, 正数入账, 负数消费")
    private Integer delta;

    @Schema(description = "变动后余额快照")
    private Integer balanceAfter;

    @Schema(description = "来源: 0签到基础 1签到连签奖励 2商城购买 3退款回补 99管理员调整")
    private Byte sourceType;

    @Schema(description = "关联业务行ID(checkin_log.id / user_emoji.id 等, 可空)")
    private Long relatedId;

    @Schema(description = "人类可读描述")
    private String remark;

    @Schema(description = "业务幂等键，一次性扣费/发奖必填")
    private String idempotencyKey;

    @Schema(description = "是否删除: 0否 1是")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
