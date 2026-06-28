package org.example.forumdemo.entity.dto.ai;

import lombok.Data;

@Data
public class AiImageRequest {

    private String prompt;

    /** normal | premium */
    private String quality;

    /** companion drawing session id (optional) */
    private String sessionId;

    /** article id for cover image generation (optional) */
    private Long articleId;

    /** skip persisting to companion session table */
    private Boolean ephemeral;

    /** use points billing when quota is exhausted */
    private Boolean usePointsBilling;

    /** 客户端幂等键，重试时必须相同 */
    private String clientRequestId;
}
