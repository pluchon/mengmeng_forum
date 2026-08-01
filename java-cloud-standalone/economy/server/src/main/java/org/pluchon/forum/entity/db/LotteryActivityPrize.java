package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@TableName("lottery_activity_prize")
@Schema(description = "活动奖品关联")
public class LotteryActivityPrize {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long prizeId;

    private Integer weight;

    @Schema(description = "-1不限量")
    private Integer stockRemaining;

    @Schema(description = "1头奖动效")
    private Byte isJackpot;

    private String imagePath;

    @JsonIgnore
    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
