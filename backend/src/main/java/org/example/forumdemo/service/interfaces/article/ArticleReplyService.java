package org.example.forumdemo.service.interfaces.article;

import org.example.forumdemo.entity.dto.article.ReplyArticleRequest;
import org.example.forumdemo.entity.vo.article.ArticleReplyListResponse;
import org.example.forumdemo.entity.vo.common.PageResult;

/**
 * 帖子一级回复（楼层）
 */
public interface ArticleReplyService {

    void replyArticle(ReplyArticleRequest replyArticleRequest, Long loginUserId);

    // 帖子楼层列表（分页）
    PageResult<ArticleReplyListResponse> queryReplyByArticleIdWithPage(Long articleId, Integer pageNum, Integer pageSize);

    void deleteReply(Long replyId, Long loginUserId);
}
