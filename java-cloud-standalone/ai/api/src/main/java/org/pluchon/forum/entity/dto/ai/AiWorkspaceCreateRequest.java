package org.pluchon.forum.entity.dto.ai;

import lombok.Data;

// 创建 AI 创作工作区请求
@Data
public class AiWorkspaceCreateRequest {

    private Long companionSessionId;

    private String checkpointId;
}
