package org.example.forumdemo.entity.dto.ai;

import lombok.Data;

/** Token/image usage returned from ai-server. */
@Data
public class AiModelUsageDTO {

    private String modelCode;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer imageCount;
    /** true when upstream had no real token counts */
    private Boolean estimated;
    /** measured latency in ms */
    private Integer latencyMs;
}
