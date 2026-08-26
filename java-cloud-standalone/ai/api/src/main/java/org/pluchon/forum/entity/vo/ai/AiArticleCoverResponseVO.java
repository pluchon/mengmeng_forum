package org.pluchon.forum.entity.vo.ai;

import lombok.Data;
import org.pluchon.forum.entity.dto.AiModelUsageDTO;

// 帖子一键生成封面完整响应
@Data
public class AiArticleCoverResponseVO {

    // 已持久化的封面地址
    private String url;
    // 实际生图提示词
    private String prompt;
    // 实际生图模型
    private String model;
    // 是否使用MCP检索
    private Boolean mcpUsed;
    // 聚合模型用量
    private AiModelUsageDTO usage;
    // 本次积分消耗
    private Integer pointsCost;
    // 扣费后积分余额
    private Integer balanceAfter;
    // 结算模式
    private String billingMode;
    // 用量统计
    private AiUsageStatsVO usageStats;
    // AI工作区ID
    private Long workspaceId;
    // 工作区版本ID
    private Long workspaceVersionId;
}
