package org.pluchon.forum.entity.dto.article;

import lombok.Data;

/**
 * @author pluchon
 * 楼中楼回复请求 DTO
 */
//回复楼中楼请求
@Data
public class SubReplyRequest {
    //所属帖子ID
    private Long articleId;
    //所属楼层（一级回复）ID
    private Long replyId;
    //被回复的目标用户ID
    private Long replyUserId;
    //当前登录用户ID（由 Controller 从 session 注入，不暴露给前端
    private Long postUserId;
    //回复内容
    private String content;
    private java.util.List<ArticleReplyMediaItemDTO> mediaList;
}
