package org.example.forumdemo.entity.dto.article;

import lombok.Data;

import java.util.List;

// 更新帖子请求
@Data
public class UpdateArticleRequest {

    // 帖子 ID
    private Long articleId;

    // 帖子标题
    private String title;

    // 帖子正文
    private String content;

    // 帖子标签 ID，最多 5 个
    private List<Long> tagIds;
}
