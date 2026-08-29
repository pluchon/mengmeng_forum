package org.pluchon.forum.entity.dto.article;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.List;

// 更新帖子请求
// 长度约束与 PublishArticleRequest 保持一致：编辑同样整体覆盖标题与正文，
// 这里不校验的话，可以把已发布的帖子改成空标题或超长正文
@Data
public class UpdateArticleRequest {

    // 帖子 ID
    @NotNull
    private Long articleId;

    // 帖子标题
    @NotNull
    @Length(min = 1, max = 100, message = "标题最多 100 个字符")
    private String title;

    // 帖子正文
    @NotNull
    @Length(min = 1, max = 20000, message = "正文最多 20000 个字符")
    private String content;

    // 帖子标签 ID，最多 5 个
    private List<Long> tagIds;
}
