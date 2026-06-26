package org.example.forumdemo.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 井字棋房间聊天广播响应
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JinziChatVO {

    // 发送用户 ID
    private Long userId;

    // 消息类型：TEXT/EMOJI
    private String messageType;

    // 文本内容或表情 URL
    private String content;

    // 表情包 ID
    private Long emojiId;

    // 表情包图片 URL
    private String emojiUrl;

    // 发送时间戳
    private Long sentAtMs;
}
