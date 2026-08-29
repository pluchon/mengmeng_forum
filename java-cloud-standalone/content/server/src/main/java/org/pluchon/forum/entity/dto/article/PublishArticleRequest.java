package org.pluchon.forum.entity.dto.article;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.List;

// 发布帖子请求
// 这里只挡上限不挡下限：这个接口建的是草稿，草稿本来就允许写一半先存着。
// "标题≥3、正文≥6" 属于发布门槛，统一在 submitForAudit 里把关
@Data
public class PublishArticleRequest {

    // 板块 ID
    @NotNull
    private Long boardId;

    // 帖子标题
    @NotNull
    @Length(min = 1, max = 100, message = "标题最多 100 个字符")
    private String title;

    // 帖子正文
    @NotNull
    @Length(min = 1, max = 20000, message = "正文最多 20000 个字符")
    private String content;

    // 内容类型: 0富文本 1Markdown，默认0
    private Byte contentType = 0;

    // 帖子业务类型：0 普通帖，1 问答帖
    private Byte articleType = 0;

    // 帖子标签 ID，最多 5 个
    private List<Long> tagIds;
}
