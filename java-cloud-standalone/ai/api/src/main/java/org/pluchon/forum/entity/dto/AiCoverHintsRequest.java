package org.pluchon.forum.entity.dto;

import lombok.Data;

@Data
public class AiCoverHintsRequest {
    private String articleText;

    // AI 创作工作区；为空时仅返回配图建议
    private Long workspaceId;

    // 生成结果所属父版本
    private Long parentVersionId;

    // LangGraph checkpoint 标识，仅用于恢复关联
    private String checkpointId;
}
