package org.pluchon.forum.service.interfaces.moderation;

import org.pluchon.forum.entity.dto.article.ContentReportRequest;
import org.pluchon.forum.entity.vo.article.ContentReportVO;

import java.util.Map;

// 内容举报服务
public interface ContentReportService {

    ContentReportVO report(Long reporterUserId, ContentReportRequest request);

    void applyAsyncResult(Map<String, Object> result);

    int republishPendingTasks();
}
