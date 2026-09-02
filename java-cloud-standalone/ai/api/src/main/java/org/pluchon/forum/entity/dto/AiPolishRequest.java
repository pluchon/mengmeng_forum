package org.pluchon.forum.entity.dto;

import lombok.Data;

// 帖子正文一键润色请求
@Data
public class AiPolishRequest {

    private String title;

    private String content;

    private String editorMode;

    private String clientRequestId;

    private Long workspaceId;

    private Long parentVersionId;

    private String checkpointId;
    // 由 Java 按登录态填入，前端传什么都会被覆盖。
    // Python 据此决定允不允许升级到深度模型——限制必须是代码，不能靠提示词。
    private Integer vipTier;
}
