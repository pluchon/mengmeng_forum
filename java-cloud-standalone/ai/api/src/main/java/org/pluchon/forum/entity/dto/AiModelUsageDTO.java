package org.pluchon.forum.entity.dto;

import lombok.Data;

@Data
public class AiModelUsageDTO {

    // 子图调用阶段
    private String stage;
    private String modelCode;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer imageCount;
    private Boolean estimated;
    private Integer latencyMs;
}
