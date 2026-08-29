package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.dto.article.ReplyArticleRequest;
import org.pluchon.forum.entity.vo.article.ArticleReplyListResponse;
import org.pluchon.forum.entity.vo.common.PageResult;

// 帖子一级回复 楼层
public interface ArticleReplyService {

    // 返回新建的评论，供前端把它临时置顶在评论区最上面：
    // 评论按时间正序 + 无限滚动，新评论其实在最末尾，不这样用户看不到自己刚发的
    ArticleReplyListResponse replyArticle(ReplyArticleRequest replyArticleRequest, Long loginUserId);

    // 帖子楼层列表 分页
    PageResult<ArticleReplyListResponse> queryReplyByArticleIdWithPage(
            Long articleId, Integer pageNum, Integer pageSize, Long loginUserId);
}
