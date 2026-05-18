package org.example.forumdemo.entity.dto.ai;

import lombok.Data;

/**
 * Python ai-server 返回的用量（token / 张数）。
 */
@Data
public class AiModelUsageDTO {

    private String modelCode;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer imageCount;
    /** 无真实 token 时由上游标记 */
    private Boolean estimated;
}
