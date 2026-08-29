package org.pluchon.forum.entity.dto.article;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

// 作者代码水平一般，难免难看，请见谅
// 回复帖子请求
@Data
public class ReplyArticleRequest {
    @NotNull
    private Long articleId;// 帖子ID

    // 发送者以会话为准，服务层直接覆盖，这里保留仅为兼容旧前端
    private Long postUserId;

    // 与 article_reply.content varchar(500) 对齐；此前完全没有上限，
    // 绕过前端传超长内容会直接撞数据库约束报 500
    @Length(max = 500, message = "评论最多 500 个字符")
    private String content;// 内容
    private java.util.List<ArticleReplyMediaItemDTO> mediaList;
}
