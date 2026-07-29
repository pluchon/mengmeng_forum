package org.example.forumdemo.task;

import org.example.forumdemo.service.interfaces.recommendation.RecommendationAiProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 定时补齐到期的推荐画像，不进入用户请求链路
@Component
public class RecommendationProfileRefreshTask {

    @Autowired
    private RecommendationAiProfileService recommendationAiProfileService;

    @Scheduled(fixedDelay = 60L * 60 * 1000, initialDelay = 5L * 60 * 1000)
    public void refreshDueProfiles() {
        recommendationAiProfileService.refreshDueProfiles();
    }
}
