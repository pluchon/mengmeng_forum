package org.pluchon.forum.service.interfaces.message;

import org.pluchon.forum.entity.dto.message.ChatMessageReportRequest;
import org.pluchon.forum.entity.vo.message.ChatMessageReportVO;

import java.util.Map;

// 私信与群聊举报服务
public interface ChatMessageReportService {
    ChatMessageReportVO report(Long reporterUserId, ChatMessageReportRequest request);
    void applyAsyncResult(Map<String, Object> result);
    int republishPendingTasks();
}
