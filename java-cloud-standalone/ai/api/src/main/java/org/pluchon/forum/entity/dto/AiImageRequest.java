package org.pluchon.forum.entity.dto;

import lombok.Data;

@Data
public class AiImageRequest {

    private String prompt;

    private String quality;

    private String sessionId;

    private Long articleId;

    private Boolean ephemeral;

    private String clientRequestId;

    private Long workspaceId;

    private Long parentVersionId;

    private String checkpointId;
}
