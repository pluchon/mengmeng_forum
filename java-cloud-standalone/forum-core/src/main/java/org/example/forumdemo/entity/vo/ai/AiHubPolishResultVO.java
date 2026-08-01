package org.example.forumdemo.entity.vo.ai;

import lombok.Data;
import org.example.forumdemo.entity.dto.ai.AiModelUsageDTO;

// AI Hub 正文润色响应
@Data
public class AiHubPolishResultVO {

    private String text;
    private String model;
    private String provider;
    private AiModelUsageDTO usage;
}
