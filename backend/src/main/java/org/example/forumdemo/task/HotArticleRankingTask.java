package org.example.forumdemo.task;

import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.metrics.ForumMetrics;
import org.example.forumdemo.service.interfaces.article.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 热帖榜兜底任务.
 *  - 每天凌晨 3:00 全量重算 (cron: 0 0 3 * * ?), 纠正长期增量漂移
 *  - 应用启动完成后也跑一次, 保证 Redis 重启 / 新部署后 ZSet 恢复
 * 触发时段错开业务高峰; Redis ZSet 重建期间 getHotArticleList 仍能命中老快照, 用户无感知.
 */
@Slf4j
@Component
public class HotArticleRankingTask {

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ForumMetrics forumMetrics;

    @Scheduled(cron = "0 0 3 * * ?")
    public void rebuildDaily() {
        Timer.Sample sample = forumMetrics.startHotRankingRebuild();
        try {
            log.info("[HotArticleRankingTask] 凌晨重算开始");
            articleService.rebuildHotArticleRanking();
            log.info("[HotArticleRankingTask] 凌晨重算完成");
        } catch (Exception e) {
            log.error("[HotArticleRankingTask] 凌晨重算异常: {}", e.getMessage(), e);
        } finally {
            forumMetrics.recordHotRankingRebuild(sample);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        Timer.Sample sample = forumMetrics.startHotRankingRebuild();
        try {
            log.info("[HotArticleRankingTask] 启动重算开始");
            articleService.rebuildHotArticleRanking();
            log.info("[HotArticleRankingTask] 启动重算完成");
        } catch (Exception e) {
            log.warn("[HotArticleRankingTask] 启动重算异常(可忽略, 下次定时仍会补): {}", e.getMessage());
        } finally {
            forumMetrics.recordHotRankingRebuild(sample);
        }
    }
}
