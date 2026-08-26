package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.common.enums.HotArticleTrendDirection;

import java.util.List;
import java.util.Map;

// 帖子热榜 ZSet 维护与综合分计算
public interface ArticleHotRankingService {

    List<Long> getHotArticleList(Integer topN);

    PageResult<Long> getHotArticlePage(Integer pageNum, Integer pageSize);

    void rebuildHotArticleRanking();

    double computeHotScore(Article article);

    void addToHotRanking(Long articleId);

    void incrementScore(Long articleId, double delta);

    void removeFromRanking(Long articleId);

    Map<Long, HotArticleTrendDirection> getTrendDirections(List<Long> articleIds);

    // 批量读取当前热榜综合分
    Map<Long, Double> getHotScores(List<Long> articleIds);
}
