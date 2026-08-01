package org.pluchon.forum.service.interfaces.recommendation;

import org.pluchon.forum.entity.dto.recommendation.NotInterestedArticleRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.recommendation.RecommendArticleVO;

// 规则推荐流服务
public interface RecommendationService {

    PageResult<RecommendArticleVO> getFeed(Long loginUserId, Integer pageNum, Integer pageSize);

    void markNotInterested(Long loginUserId, NotInterestedArticleRequest request);

    PageResult<RecommendArticleVO> getNotInterestedArticles(Long loginUserId, Integer pageNum, Integer pageSize);

    void restoreInterested(Long loginUserId, Long articleId);
}
