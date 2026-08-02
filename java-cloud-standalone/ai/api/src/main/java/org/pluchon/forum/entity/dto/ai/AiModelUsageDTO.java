package org.pluchon.forum.entity.dto.ai;

import lombok.Data;

@Data
public class AiModelUsageDTO {

    private String modelCode;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer imageCount;
    private Boolean estimated;
    private Integer latencyMs;
}
