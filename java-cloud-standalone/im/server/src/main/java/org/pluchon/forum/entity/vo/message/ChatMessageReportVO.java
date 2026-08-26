package org.pluchon.forum.entity.vo.message;

import lombok.Data;

// 聊天举报提交结果
@Data
public class ChatMessageReportVO {
    private Long reportId;
    private String status;
    private String message;
}
