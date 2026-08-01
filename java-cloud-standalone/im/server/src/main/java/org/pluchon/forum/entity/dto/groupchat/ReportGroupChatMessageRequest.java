package org.pluchon.forum.entity.dto.groupchat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 举报群消息请求
@Data
public class ReportGroupChatMessageRequest {

    // 群消息 ID
    @NotNull
    private Long messageId;

    // 举报原因
    @NotBlank
    private String reason;
}
