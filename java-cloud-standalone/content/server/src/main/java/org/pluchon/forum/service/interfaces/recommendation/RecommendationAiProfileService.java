package org.pluchon.forum.service.interfaces.recommendation;

import java.util.Map;

// 推荐 AI 特征与用户画像的异步服务
public interface RecommendationAiProfileService {

    void generateArticleFeature(Long articleId);

    void requestProfileRefresh(Long userId);

    void clearProfile(Long userId);

    void refreshDueProfiles();

    Map<String, Double> getActiveTopicWeights(Long userId);

    Map<String, Double> getAvoidTopicWeights(Long userId);
}
