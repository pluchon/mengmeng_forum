package org.pluchon.forum.entity.dto.article;

import lombok.Data;

import java.util.List;

// 楼中楼回复请求
@Data
public class SubReplyRequest {
    // 帖子ID
    private Long articleId;
    // 一级回复ID
    private Long replyId;
    // 被回复用户ID
    private Long replyUserId;
    // 发送用户ID
    private Long postUserId;
    // 回复内容
    private String content;
    // 媒体附件列表
    private List<ArticleReplyMediaItemDTO> mediaList;
}
