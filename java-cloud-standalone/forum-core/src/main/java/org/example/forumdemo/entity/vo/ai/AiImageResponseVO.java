package org.example.forumdemo.entity.vo.ai;

import lombok.Data;
import org.example.forumdemo.entity.dto.ai.AiModelUsageDTO;

// AI 生图接口完整响应（含计费摘要）
@Data
public class AiImageResponseVO {

    private String url;
    private String model;
    private String modelCode;
    private String sessionId;
    private AiModelUsageDTO usage;
    private Integer pointsCost;
    private Integer balanceAfter;
    private String billingMode;
    private AiUsageStatsVO usageStats;
    private Long workspaceId;
    private Long workspaceVersionId;
}
