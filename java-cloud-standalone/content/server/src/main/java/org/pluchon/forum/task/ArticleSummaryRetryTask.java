package org.pluchon.forum.task;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.service.interfaces.article.ArticleSummaryService;
import org.pluchon.forum.service.interfaces.article.ArticleUserMusicAuditService;
import org.pluchon.forum.service.interfaces.moderation.ContentModerationTaskService;
import org.pluchon.forum.service.interfaces.moderation.ContentReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 帖子总结待处理任务补投
@Slf4j
@Component
public class ArticleSummaryRetryTask {

    @Autowired
    private ArticleSummaryService articleSummaryService;

    @Autowired
    private ArticleUserMusicAuditService articleUserMusicAuditService;

    @Autowired
    private ContentModerationTaskService contentModerationTaskService;

    @Autowired
    private ContentReportService contentReportService;

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void republishPending() {
        int count = articleSummaryService.republishPendingTasks()
                + articleUserMusicAuditService.republishPendingTasks()
                + contentModerationTaskService.republishPendingTasks()
                + contentReportService.republishPendingTasks();
        if (count > 0) {
            log.info("Content域AI任务补投完成 count={}", count);
        }
    }
}
