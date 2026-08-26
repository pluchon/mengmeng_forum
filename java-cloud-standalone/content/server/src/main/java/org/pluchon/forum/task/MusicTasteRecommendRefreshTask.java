package org.pluchon.forum.task;

import org.pluchon.forum.service.interfaces.article.ArticleMusicRecommendRefreshService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 双周音乐品味片单：每周日 03:10 触发，服务内再判断是否落在双周桶执行日（约每 14 天一次）
@ConditionalOnProperty(name = "forum.domain", havingValue = "content")
@Component
public class MusicTasteRecommendRefreshTask {

    @Autowired
    private ArticleMusicRecommendRefreshService articleMusicRecommendRefreshService;

    @Scheduled(cron = "0 10 3 ? * SUN", zone = "Asia/Shanghai")
    public void refreshBiweeklySlates() {
        if (!articleMusicRecommendRefreshService.shouldRunBiweeklyRefresh()) {
            return;
        }
        articleMusicRecommendRefreshService.refreshBiweeklySlates();
    }
}
