package org.pluchon.forum.task;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.cloud.ForumDomainNames;
import org.pluchon.forum.service.interfaces.article.ArticleVideoTranscodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 帖子视频 HLS 转码兜底任务
//
// 转码任务只存在于进程内的线程池里：服务重启、线程池队列拒绝、进程被杀，
// 任务都会凭空消失，而帖子会永远停在 PROCESSING，没有任何机制去修复。
// 审核链路早就有 ArticleAuditTimeoutTask 兜底，转码这边一直缺一个对称的。
//
// 用 fixedDelay 而非 cron，保证上一轮跑完再隔 10 分钟，不会并发跑。
// 重复入队由 processTranscode 里的 Redis 锁挡住。
@Slf4j
@ConditionalOnProperty(name = "forum.domain", havingValue = ForumDomainNames.CONTENT)
@Component
public class ArticleVideoTranscodeSweepTask {

    @Autowired
    private ArticleVideoTranscodeService articleVideoTranscodeService;

    @Scheduled(fixedDelay = 10L * 60 * 1000, initialDelay = 3L * 60 * 1000)
    public void sweep() {
        try {
            int handled = articleVideoTranscodeService.sweepStuckTranscodes();
            if (handled > 0) {
                log.warn("[ArticleVideoTranscodeSweepTask] 重新入队 {} 条卡死的转码任务", handled);
            }
        } catch (Exception e) {
            log.error("[ArticleVideoTranscodeSweepTask] 扫描异常: {}", e.getMessage(), e);
        }
    }
}
