package org.example.forumdemo.entity.vo.ai;

import lombok.Data;
import org.example.forumdemo.entity.dto.ai.AiModelUsageDTO;

// 帖子正文一键润色响应
@Data
public class AiPolishResponseVO {

    private String text;
    private String model;
    private String provider;
    private AiModelUsageDTO usage;
    private Integer pointsCost;
    private Integer balanceAfter;
    private String billingMode;
    private AiUsageStatsVO usageStats;
    private Long workspaceId;
    private Long workspaceVersionId;
}
