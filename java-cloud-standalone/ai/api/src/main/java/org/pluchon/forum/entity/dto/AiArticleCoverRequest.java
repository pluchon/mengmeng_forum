package org.pluchon.forum.entity.dto;

import lombok.Data;

// 帖子正文一键生成封面请求
@Data
public class AiArticleCoverRequest {

    // 帖子标题
    private String title;

    // 帖子正文
    private String content;

    // 编辑器模式
    private String editorMode;

    // 用户补充画面要求
    private String userPrompt;

    // 生图质量档位
    private String quality;

    // 客户端幂等请求ID
    private String clientRequestId;

    // AI工作区ID
    private Long workspaceId;

    // 父版本ID
    private Long parentVersionId;

    // 工作区检查点ID
    private String checkpointId;
}
