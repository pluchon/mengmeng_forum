package org.example.forumdemo.entity.dto.ai;

import lombok.Data;

// 新增 AI 创作产物版本请求
@Data
public class AiWorkspaceArtifactRequest {

    private Long parentVersionId;

    private String artifactType;

    private String artifactJson;

    private String checkpointId;
}
