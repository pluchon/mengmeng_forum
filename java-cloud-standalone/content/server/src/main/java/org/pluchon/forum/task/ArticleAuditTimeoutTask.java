package org.pluchon.forum.task;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.cloud.ForumDomainNames;
import org.pluchon.forum.service.interfaces.article.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 帖子审核超时兜底任务. 触发场景: Python 服务挂掉 / 队列堵塞 / MQ 网络抖动 > 帖子永远卡在 PENDING_AUDIT 每 5 分钟扫描一次, 把 audit_submitted_at + ARTICLE_AUDIT_TIMEOUT_SECONDS 仍在 PENDING 的转为 AUDIT_ERROR 用户随后可继续 submitForAudit 重试 受 retry 上限约束 用 fixedDelay 而非 cron, 保证上一次跑完之后再隔 5 分钟, 不会并发跑.
@Slf4j
@ConditionalOnProperty(name = "forum.domain", havingValue = ForumDomainNames.CONTENT)
@Component
public class ArticleAuditTimeoutTask {

    @Autowired
    private ArticleService articleService;

    @Scheduled(fixedDelay = 5L * 60 * 1000, initialDelay = 60L * 1000)
    public void sweep() {
        try {
            int handled = articleService.sweepStuckAuditTasks();
            if (handled > 0) {
                log.warn("[ArticleAuditTimeoutTask] 兜底处理 {} 条卡死审核任务", handled);
            }
        } catch (Exception e) {
            log.error("[ArticleAuditTimeoutTask] 扫描异常: {}", e.getMessage(), e);
        }
    }
}
