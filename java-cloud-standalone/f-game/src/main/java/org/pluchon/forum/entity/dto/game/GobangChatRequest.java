package org.pluchon.forum.entity.dto.game;

import lombok.Data;

// 五子棋房间聊天请求
@Data
public class GobangChatRequest {

    // 消息类型：TEXT/EMOJI
    private String messageType;

    // 文本内容
    private String content;

    // 表情包 ID
    private Long emojiId;

    // 表情包图片 URL
    private String emojiUrl;
}
