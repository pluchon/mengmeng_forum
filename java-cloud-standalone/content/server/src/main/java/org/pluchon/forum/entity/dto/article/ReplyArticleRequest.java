package org.pluchon.forum.entity.dto.article;

import lombok.Data;

/**
 * @author pluchon
 * @create 2026-03-09-15:10
 * 作者代码水平一般，难免难看，请见谅
 */
//回复帖子请求
@Data
public class ReplyArticleRequest {
    private Long articleId;//帖子ID
    private Long postUserId;//发送者ID
    private String content;//内容
    private java.util.List<ArticleReplyMediaItemDTO> mediaList;
}
