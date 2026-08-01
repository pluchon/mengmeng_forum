package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 五子棋房间聊天广播响应
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GobangChatVO {

    // 发送用户 ID
    private Long userId;

    // 消息类型：TEXT/EMOJI
    private String messageType;

    // 文本内容
    private String content;

    // 表情包 ID
    private Long emojiId;

    // 表情包图片 URL
    private String emojiUrl;

    // 发送时间戳
    private Long sentAtMs;
}
