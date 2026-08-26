package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

// 签到惊喜奖池
@Data
@TableName("checkin_surprise_pool")
@Schema(description = "签到惊喜奖池")
public class CheckinSurprisePool {

    @Schema(description = "编号, 主键, 自增")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "奖励类型: POINTS/VIP_DAYS/STARLIGHT/MAKEUP_CARD/LOTTERY_VOUCHER")
    private String rewardType;

    @Schema(description = "奖励数值")
    private Integer rewardValue;

    @Schema(description = "抽取权重")
    private Integer weight;

    @Schema(description = "展示文案")
    private String label;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "是否删除: 0否 1是")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
