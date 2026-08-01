package org.pluchon.forum.entity.vo.ai;

import lombok.Data;
import org.pluchon.forum.entity.dto.ai.AiModelUsageDTO;

// AI Hub 生图接口响应
@Data
public class AiHubImageResultVO {

    private String url;
    private String model;
    private AiModelUsageDTO usage;
}
