package org.example.forumdemo.entity.dto.ai;

import lombok.Data;

// AI Supervisor 接力状态请求
@Data
public class AiTaskHandoffRequest {

    private Long companionSessionId;

    private Long workspaceId;

    private String activeModule;

    private String activeWorker;

    private String checkpointId;

    private String taskMode;
}
