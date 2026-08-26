package org.pluchon.forum.entity.dto.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 聊天文本消息举报请求
@Data
public class ChatMessageReportRequest {
    @NotBlank
    private String conversationType;
    @NotNull
    private Long messageId;
    @NotBlank
    @Size(min = 5, max = 200)
    private String reason;
}
