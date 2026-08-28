package org.pluchon.forum.service.interfaces.moderation;

import java.util.Map;

// 评论异步审核任务服务
public interface ContentModerationTaskService {

    void scheduleComment(Byte targetType, Long targetId, String content);

    void scheduleDanmaku(Long targetId, String content);

    void applyAsyncResult(Map<String, Object> result);

    int republishPendingTasks();

    void deleteConfirmedViolation(Byte targetType, Long targetId, String contentHash);
}
