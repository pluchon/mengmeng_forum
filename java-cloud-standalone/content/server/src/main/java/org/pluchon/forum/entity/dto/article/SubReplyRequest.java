package org.pluchon.forum.entity.dto.article;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.List;

// 楼中楼回复请求
@Data
public class SubReplyRequest {
    // 帖子ID
    @NotNull
    private Long articleId;
    // 一级回复ID
    @NotNull
    private Long replyId;
    // 被回复用户ID
    private Long replyUserId;
    // 发送用户ID。以会话为准，服务层直接覆盖，保留仅为兼容旧前端
    private Long postUserId;
    // 回复内容。article_sub_reply.content 是 text，理论上能塞 6 万字进去，
    // 前端渲染会卡死。与一级评论统一到 500 字
    @Length(max = 500, message = "回复最多 500 个字符")
    private String content;
    // 媒体附件列表
    private List<ArticleReplyMediaItemDTO> mediaList;
}
