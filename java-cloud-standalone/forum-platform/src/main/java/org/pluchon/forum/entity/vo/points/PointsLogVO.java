package org.pluchon.forum.entity.vo.points;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 积分流水条目. 给前端"积分明细"分页列表用.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "积分流水条目")
public class PointsLogVO {

    @Schema(description = "流水ID")
    private Long id;

    @Schema(description = "本次变动量, 正数入账, 负数消费")
    private Integer delta;

    @Schema(description = "变动后余额快照")
    private Integer balanceAfter;

    @Schema(description = "来源: 0签到基础 1签到连签奖励 2商城购买 3退款回补 99管理员调整")
    private Byte sourceType;

    @Schema(description = "关联业务ID")
    private Long relatedId;

    @Schema(description = "人类可读描述, 例如 '签到 +5'")
    private String remark;

    @Schema(description = "发生时间")
    private Date createTime;
}
