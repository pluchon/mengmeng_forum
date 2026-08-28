package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.dto.article.ReplyArticleRequest;
import org.pluchon.forum.entity.vo.article.ArticleReplyListResponse;
import org.pluchon.forum.entity.vo.common.PageResult;

// 帖子一级回复 楼层
public interface ArticleReplyService {

    void replyArticle(ReplyArticleRequest replyArticleRequest, Long loginUserId);

    // 帖子楼层列表 分页
    PageResult<ArticleReplyListResponse> queryReplyByArticleIdWithPage(
            Long articleId, Integer pageNum, Integer pageSize, Long loginUserId);
}
