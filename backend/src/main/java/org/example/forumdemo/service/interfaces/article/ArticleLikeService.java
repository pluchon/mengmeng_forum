package org.example.forumdemo.service.interfaces.article;

import org.example.forumdemo.entity.vo.article.ArticleListByLikeResponse;
import org.example.forumdemo.entity.vo.common.PageResult;

/**
 * 帖子点赞模块
 */
public interface ArticleLikeService {

    void likeArticle(Long articleId, Long userId);

    void unlikeArticle(Long articleId, Long userId);

    // 当前用户点赞过的帖子列表（分页）
    PageResult<ArticleListByLikeResponse> queryArticleListForLikeWithPage(Long userId, Integer pageNum, Integer pageSize);

}
