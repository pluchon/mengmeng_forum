package org.pluchon.forum.service.impl.article;

import org.pluchon.forum.service.interfaces.ai.AiHubService;
import org.pluchon.forum.service.interfaces.article.ArticleHotRankingService;
import org.pluchon.forum.service.interfaces.article.ArticlePublishSideEffectService;
import org.pluchon.forum.service.interfaces.board.BoardService;
import org.pluchon.forum.service.interfaces.search.ArticleSearchIndexService;
import org.pluchon.forum.cloud.feign.ContentUserInternalFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.pluchon.forum.common.constant.Constant;

// 帖子发布/下线时的用户帖数、板块帖数、热榜、搜索、RAG 与摘要缓存联动
@Service
public class ArticlePublishSideEffectServiceImpl implements ArticlePublishSideEffectService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private BoardService boardService;

    @Autowired
    private ContentUserInternalFeignClient contentUserInternalFeignClient;

    @Autowired
    private ArticleSearchIndexService articleSearchIndexService;

    @Autowired
    private ArticleHotRankingService articleHotRankingService;

    @Autowired
    private AiHubService aiHubService;

    @Override
    public void rollbackPublishedExposure(Long articleId, Long boardId, Long userId) {
        articleHotRankingService.removeFromRanking(articleId);
        boardService.deleteOneById(boardId);
        contentUserInternalFeignClient.decrementArticleCount(userId);
        stringRedisTemplate.delete(Constant.REDIS_KEY_ARTICLE_SUMMARY + articleId);
        articleSearchIndexService.removeArticle(articleId);
        aiHubService.removeArticleRag(articleId);
    }

    @Override
    public void promotePublishedExposure(Long articleId, Long userId, Long boardId) {
        contentUserInternalFeignClient.incrementArticleCount(userId);
        boardService.addOneById(boardId);
    }
}
