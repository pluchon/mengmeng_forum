package org.pluchon.forum.entity.dto.groupchat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 发送群消息请求
@Data
public class SendGroupChatMessageRequest {

    // 群聊 ID
    @NotNull
    private Long groupId;

    // 消息类型: 0文本 1表情 2图片
    @NotNull
    private Byte messageType;

    // 消息内容
    @NotBlank
    private String content;
    // 本条消息 @ 到的成员 ID。前端选人时就知道是谁，不该让服务端去猜昵称
    private java.util.List<Long> mentionedUserIds;

    // 回复的群消息 ID
    private Long replyMessageId;

    // 商城表情包 ID；发送商城表情时必填
    private Long emojiShopId;
}
