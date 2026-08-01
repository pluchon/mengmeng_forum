package org.example.forumdemo.entity.vo.ai;

import lombok.Data;
import org.example.forumdemo.entity.dto.ai.AiModelUsageDTO;

// AI Hub 生图接口响应
@Data
public class AiHubImageResultVO {

    private String url;
    private String model;
    private AiModelUsageDTO usage;
}
