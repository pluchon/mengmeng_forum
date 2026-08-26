package org.pluchon.forum.service.interfaces.article;

import java.util.Map;

// 用户歌曲 AI 审核异步任务
public interface ArticleUserMusicAuditService {

    void scheduleAudit(Long userMusicId);

    void applyAsyncResult(Map<String, Object> result);

    int republishPendingTasks();
}
