package org.example.forumdemo.entity.vo.ai;

import lombok.Data;

import java.util.Date;

// AI Supervisor 当前接力状态
@Data
public class AiTaskSessionVO {

    private Long id;
    private Long companionSessionId;
    private Long workspaceId;
    private String activeModule;
    private String activeWorker;
    private String checkpointId;
    private String taskMode;
    private String taskState;
    private Date updateTime;
}
