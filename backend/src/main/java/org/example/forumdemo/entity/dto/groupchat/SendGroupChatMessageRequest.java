package org.example.forumdemo.entity.dto.groupchat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 发送群消息请求
@Data
public class SendGroupChatMessageRequest {

    // 群聊 ID
    @NotNull
    private Long groupId;

    // 消息类型: 0文本 1表情
    @NotNull
    private Byte messageType;

    // 消息内容
    @NotBlank
    private String content;
}
