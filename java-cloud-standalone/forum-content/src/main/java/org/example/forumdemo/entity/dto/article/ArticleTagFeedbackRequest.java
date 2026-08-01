package org.example.forumdemo.entity.dto.article;

import lombok.Data;

// 用户申请新增帖子标签请求
@Data
public class ArticleTagFeedbackRequest {
    private Long boardId;
    private String proposedName;
}
