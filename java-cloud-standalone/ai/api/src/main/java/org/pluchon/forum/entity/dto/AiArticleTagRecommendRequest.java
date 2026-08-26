package org.pluchon.forum.entity.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

// 帖子标签 AI 推荐请求
@Data
public class AiArticleTagRecommendRequest {

    private Long userId;

    private String clientRequestId;

    // 帖子标题
    private String title;

    // 帖子正文
    private String content;

    // 编辑器模式
    private String editorMode;

    // 当前版块允许使用的标签
    @NotEmpty
    private List<AiArticleTagCandidateDTO> candidates;
}
