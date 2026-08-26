package org.pluchon.forum.entity.dto.starlight;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "萌星辉商城兑换请求")
public class StarlightExchangeDTO {

    @Schema(description = "商品 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;

    @Schema(description = "客户端幂等键，同一兑换重试必须复用", requiredMode = Schema.RequiredMode.REQUIRED)
    private String requestId;
}
