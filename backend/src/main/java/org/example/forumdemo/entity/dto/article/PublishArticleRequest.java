package org.example.forumdemo.entity.dto.article;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.List;

// 发布帖子请求
@Data
public class PublishArticleRequest {

    // 板块 ID
    @NotNull
    private Long boardId;

    // 帖子标题
    @NotNull
    @Length(min = 3, message = "最少输入3个字符")
    private String title;

    // 帖子正文
    @NotNull
    @Length(min = 6, message = "最少输入6个字符内容")
    private String content;

    // 内容类型: 0富文本 1Markdown，默认0
    private Byte contentType = 0;

    // 帖子业务类型：0 普通帖，1 问答帖
    private Byte articleType = 0;

    // 帖子标签 ID，最多 5 个
    private List<Long> tagIds;
}
