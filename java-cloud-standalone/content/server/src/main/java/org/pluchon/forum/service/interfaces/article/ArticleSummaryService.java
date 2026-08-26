package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.vo.article.ArticleSummaryVO;

import java.util.Map;

// 帖子AI总结任务与持久化服务
public interface ArticleSummaryService {

    void scheduleInitialSummary(Long articleId);

    ArticleSummaryVO getSummary(Long articleId);

    ArticleSummaryVO regenerate(Long articleId, Long loginUserId);

    void applyAsyncResult(Map<String, Object> result);

    int republishPendingTasks();
}
