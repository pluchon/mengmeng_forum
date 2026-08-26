package org.pluchon.forum.entity.dto.article;

import lombok.Data;

// 帖子标签 AI 推荐请求
@Data
public class ArticleTagSuggestRequest {

    // 发布版块主键
    private Long boardId;

    // 帖子标题
    private String title;

    // 帖子正文
    private String content;

    // 编辑器模式
    private String editorMode;
}
