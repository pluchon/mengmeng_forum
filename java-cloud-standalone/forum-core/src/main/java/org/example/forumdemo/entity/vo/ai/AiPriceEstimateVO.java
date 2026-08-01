package org.example.forumdemo.entity.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 调用预估积分")
public class AiPriceEstimateVO {

    @Schema(description = "预估消耗积分")
    private Integer points;

    @Schema(description = "模型 code")
    private String modelCode;

    @Schema(description = "是否按默认 token 估算")
    private Boolean estimated;
}
