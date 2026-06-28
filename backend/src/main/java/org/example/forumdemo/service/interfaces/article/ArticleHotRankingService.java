package org.example.forumdemo.service.interfaces.article;

import org.example.forumdemo.entity.db.Article;

import java.util.List;

// 帖子热榜 ZSet 维护与综合分计算
public interface ArticleHotRankingService {

    List<Long> getHotArticleList(Integer topN);

    void rebuildHotArticleRanking();

    double computeHotScore(Article article);

    void addToHotRanking(Long articleId);

    void incrementScore(Long articleId, double delta);

    void removeFromRanking(Long articleId);
}
