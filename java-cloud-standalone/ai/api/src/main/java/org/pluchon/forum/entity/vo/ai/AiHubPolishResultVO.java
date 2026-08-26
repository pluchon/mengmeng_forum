package org.pluchon.forum.entity.vo.ai;

import lombok.Data;
import org.pluchon.forum.entity.dto.AiModelUsageDTO;

import java.util.List;

// AI Hub 正文润色响应
@Data
public class AiHubPolishResultVO {

    private String text;
    private String model;
    private String provider;
    private AiModelUsageDTO usage;
    // 按实际模型聚合的用量
    private List<AiModelUsageDTO> usageItems;
}
