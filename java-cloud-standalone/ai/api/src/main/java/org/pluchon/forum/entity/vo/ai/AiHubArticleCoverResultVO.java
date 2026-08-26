package org.pluchon.forum.entity.vo.ai;

import lombok.Data;
import org.pluchon.forum.entity.dto.AiModelUsageDTO;

import java.util.List;

// AI Hub 文章封面子图响应
@Data
public class AiHubArticleCoverResultVO {

    // 临时封面地址
    private String url;
    // 实际生图提示词
    private String prompt;
    // 实际生图模型
    private String model;
    // 是否使用MCP检索
    private Boolean mcpUsed;
    // 聚合模型用量
    private AiModelUsageDTO usage;
    // 按实际模型聚合的用量
    private List<AiModelUsageDTO> usageItems;
}
