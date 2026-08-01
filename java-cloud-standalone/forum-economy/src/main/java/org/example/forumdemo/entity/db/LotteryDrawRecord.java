package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@TableName("lottery_draw_record")
@Schema(description = "抽奖记录")
public class LotteryDrawRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long activityId;

    private Long activityPrizeId;

    private Long prizeId;

    private String prizeName;

    private Byte prizeType;

    private Integer prizeValue;

    private Integer grantPoints;

    private Byte isJackpot;

    @Schema(description = "神秘子项类型 4积分/5VIP天")
    private Byte mysteryItemType;

    @Schema(description = "神秘子项数值")
    private Integer mysteryItemValue;

    private String drawBatchKey;

    @JsonIgnore
    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
