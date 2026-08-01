package org.pluchon.forum.entity.vo.ai;

import lombok.Data;
import org.pluchon.forum.entity.dto.ai.AiModelUsageDTO;

// AI Hub 正文润色响应
@Data
public class AiHubPolishResultVO {

    private String text;
    private String model;
    private String provider;
    private AiModelUsageDTO usage;
}
