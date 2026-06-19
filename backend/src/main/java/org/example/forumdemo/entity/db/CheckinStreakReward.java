package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 连续签到奖励表实体类
 */
@Data
@TableName("checkin_streak_reward")
@Schema(description = "连续签到奖励实体")
public class CheckinStreakReward {

    @Schema(description = "编号, 主键, 自增")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "连续签到天数门槛")
    private Integer streakDays;

    @Schema(description = "额外奖励积分")
    private Integer bonusPoints;

    @Schema(description = "奖励描述")
    private String description;

    @Schema(description = "是否删除: 0否 1是")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
