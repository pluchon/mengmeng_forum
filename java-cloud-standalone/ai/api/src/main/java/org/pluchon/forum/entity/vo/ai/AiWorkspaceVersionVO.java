package org.pluchon.forum.entity.vo.ai;

import lombok.Data;

import java.util.Date;

// AI 创作产物版本
@Data
public class AiWorkspaceVersionVO {

    private Long id;
    private Long parentVersionId;
    private String artifactType;
    private Integer versionNo;
    private String artifactJson;
    private Boolean selected;
    private Date createTime;
}
