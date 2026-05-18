package org.example.forumdemo.service.interfaces.article;

import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.article.ArticleListByLikeResponse;
import org.example.forumdemo.entity.vo.common.PageResult;

import java.util.List;

/**
 * 帖子点赞模块
 */
public interface ArticleLikeService {

    void likeArticle(Long articleId, Long userId);

    void unlikeArticle(Long articleId, Long userId);

    // 当前用户点赞过的帖子列表（分页）
    PageResult<ArticleListByLikeResponse> queryArticleListForLikeWithPage(Long userId, Integer pageNum, Integer pageSize);

    // 帖子作者本人查看：谁点赞了我的帖子
    List<User> queryWhoLikedArticle(Long articleId, Long loginUserId);

    // 帖子作者本人查看：最新点赞我的帖子的 N 位用户
    List<User> getLatestLikerUsers(Long articleId, Long loginUserId, Integer count);
}
