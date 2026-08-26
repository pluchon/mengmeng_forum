package org.pluchon.forum.entity.dto.starlight;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "萌星辉背包使用请求")
public class StarlightUseDTO {

    @Schema(description = "兑换记录 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long exchangeId;
}
