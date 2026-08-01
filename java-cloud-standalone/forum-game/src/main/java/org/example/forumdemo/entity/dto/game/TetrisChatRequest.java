package org.example.forumdemo.entity.dto.game;

import lombok.Data;

// 俄罗斯方块 PK 房间聊天请求
@Data
public class TetrisChatRequest {

    // 消息类型：TEXT/EMOJI
    private String messageType;

    // 文本内容
    private String content;

    // 表情包 ID
    private Long emojiId;

    // 表情包图片 URL
    private String emojiUrl;
}
