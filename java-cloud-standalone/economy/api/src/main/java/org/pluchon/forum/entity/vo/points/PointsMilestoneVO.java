package org.pluchon.forum.entity.vo.points;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

// 萌币里程碑及领取状态
@Data
@Schema(description = "萌币里程碑")
public class PointsMilestoneVO {

    @Schema(description = "里程碑编码", example = "M1000")
    private String code;

    @Schema(description = "累计获得门槛", example = "1000")
    private Integer threshold;

    @Schema(description = "可领取萌币", example = "50")
    private Integer reward;

    @Schema(description = "名称", example = "萌新旅人")
    private String title;

    @Schema(description = "状态：LOCKED / CLAIMABLE / CLAIMED")
    private String status;
}
