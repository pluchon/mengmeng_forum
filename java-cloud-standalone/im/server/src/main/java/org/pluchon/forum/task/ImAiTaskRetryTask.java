package org.pluchon.forum.task;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.service.interfaces.message.ChatMessageReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// IM域AI待处理任务补投
@Slf4j
@Component
public class ImAiTaskRetryTask {

    @Autowired
    private ChatMessageReportService chatMessageReportService;

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void republishPending() {
        int count = chatMessageReportService.republishPendingTasks();
        if (count > 0) {
            log.info("IM域AI任务补投完成 count={}", count);
        }
    }
}
