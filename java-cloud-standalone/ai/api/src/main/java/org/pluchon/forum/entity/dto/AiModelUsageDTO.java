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
    // 按次计费的服务（如 Tavily 联网检索）用它；token 类模型留空
    private Integer callCount;
    private Boolean estimated;
    private Integer latencyMs;
}
