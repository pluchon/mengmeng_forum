package org.example.forumdemo.entity.vo.ai;

import lombok.Data;
import org.example.forumdemo.entity.dto.ai.AiModelUsageDTO;

// AI 写作接口完整响应（含计费摘要）
@Data
public class AiWriteResponseVO {

    private String text;
    private String model;
    private String provider;
    private AiModelUsageDTO usage;
    private Integer pointsCost;
    private Integer balanceAfter;
    private String billingMode;
    private AiUsageStatsVO usageStats;
}
