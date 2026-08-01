package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 签到流水表实体类
 */
@Data
@TableName("checkin_log")
@Schema(description = "签到流水实体")
public class CheckinLog {

    @Schema(description = "编号, 主键, 自增")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户编号")
    private Long userId;

    @Schema(description = "签到日期")
    private Date checkinDate;

    @Schema(description = "本次签到获得基础积分")
    private Integer points;

    @Schema(description = "本次连续签到额外奖励积分")
    private Integer bonusPoints;

    @Schema(description = "签到时的连续天数快照")
    private Integer streakDays;

    @Schema(description = "是否删除: 0否 1是")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
