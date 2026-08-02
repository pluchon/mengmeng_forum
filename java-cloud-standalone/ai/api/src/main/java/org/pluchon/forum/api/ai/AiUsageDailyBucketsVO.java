package org.pluchon.forum.api.ai;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

// AI 日用量 + 周期 token 汇总
@Data
public class AiUsageDailyBucketsVO {
    private Integer qwenFlashUsed;
    private Integer advancedLlmUsed;
    private Integer imageNormalUsed;
    private Integer imagePremiumUsed;
    private Integer companionNormalUsed;
    private Integer companionPremiumUsed;
    private Integer totalCalls;
    private Map<String, Long> tokenByModel = new HashMap<>();
}
