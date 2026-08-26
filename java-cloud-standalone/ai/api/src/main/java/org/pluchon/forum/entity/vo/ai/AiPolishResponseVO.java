package org.pluchon.forum.entity.vo.ai;

import lombok.Data;
import org.pluchon.forum.entity.dto.AiModelUsageDTO;

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
