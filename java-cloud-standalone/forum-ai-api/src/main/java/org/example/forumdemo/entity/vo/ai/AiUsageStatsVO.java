package org.example.forumdemo.entity.vo.ai;

import lombok.Data;

// AI 调用用量与计费统计（写入响应 VO）
@Data
public class AiUsageStatsVO {

    private String modelCode;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer imageCount;
    private Integer latencyMs;
    private Boolean estimated;
    private String billingMode;
    private Integer pointsCost;
}
