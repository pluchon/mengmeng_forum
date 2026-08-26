package org.pluchon.forum.entity.dto;

import lombok.Data;

// 帖子正文一键润色请求
@Data
public class AiPolishRequest {

    private String kind;

    private String title;

    private String content;

    private String editorMode;

    private Boolean usePointsBilling;

    private String clientRequestId;

    private Long workspaceId;

    private Long parentVersionId;

    private String checkpointId;
}
