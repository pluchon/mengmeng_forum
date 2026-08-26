package org.pluchon.forum.task;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.service.interfaces.article.ArticleMusicHotRankingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 音乐本周热榜：启动重算 + 每天 03:20 全量重算（错开热帖 03:00）
@Slf4j
@ConditionalOnProperty(name = "forum.domain", havingValue = "content")
@Component
public class MusicHotRankingTask {

    @Autowired
    private ArticleMusicHotRankingService articleMusicHotRankingService;

    @Scheduled(cron = "0 20 3 * * ?")
    public void rebuildDaily() {
        try {
            log.info("[MusicHotRankingTask] 凌晨重算开始");
            articleMusicHotRankingService.rebuildHotMusicRanking();
            log.info("[MusicHotRankingTask] 凌晨重算完成");
        } catch (Exception e) {
            log.error("[MusicHotRankingTask] 凌晨重算异常: {}", e.getMessage(), e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        try {
            log.info("[MusicHotRankingTask] 启动重算开始");
            articleMusicHotRankingService.rebuildHotMusicRanking();
            log.info("[MusicHotRankingTask] 启动重算完成");
        } catch (Exception e) {
            log.warn("[MusicHotRankingTask] 启动重算异常(可忽略, 下次定时仍会补): {}", e.getMessage());
        }
    }
}
