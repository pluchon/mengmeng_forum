package org.pluchon.forum.entity.vo.points;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

// 指定周期内的萌币来源汇总
@Data
@Schema(description = "萌币来源汇总")
public class PointsSourceSummaryVO {

    @Schema(description = "来源类型")
    private Byte sourceType;

    @Schema(description = "来源名称")
    private String sourceLabel;

    @Schema(description = "变动总额的绝对值")
    private Integer amount;
}
