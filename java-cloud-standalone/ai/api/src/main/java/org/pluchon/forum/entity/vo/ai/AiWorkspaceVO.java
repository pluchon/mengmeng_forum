package org.pluchon.forum.entity.vo.ai;

import lombok.Data;

import java.util.Date;

// AI 创作工作区摘要
@Data
public class AiWorkspaceVO {

    private Long id;
    private Long companionSessionId;
    private String workspaceState;
    private Long selectedVersionId;
    private String checkpointId;
    private Date createTime;
    private Date updateTime;
}
