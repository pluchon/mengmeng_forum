package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

// 签到发奖幂等流水
@Data
@TableName("checkin_grant_log")
@Schema(description = "签到发奖幂等流水")
public class CheckinGrantLog {

    @Schema(description = "编号, 主键, 自增")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户编号")
    private Long userId;

    @Schema(description = "发奖种类: STREAK/SURPRISE/MAKEUP")
    private String grantKind;

    @Schema(description = "业务幂等键")
    private String bizKey;

    @Schema(description = "奖励类型快照")
    private String rewardType;

    @Schema(description = "奖励数值快照")
    private Integer rewardValue;

    @Schema(description = "奖励文案快照")
    private String rewardLabel;

    @Schema(description = "关联签到流水 ID")
    private Long relatedId;

    @Schema(description = "是否删除: 0否 1是")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
