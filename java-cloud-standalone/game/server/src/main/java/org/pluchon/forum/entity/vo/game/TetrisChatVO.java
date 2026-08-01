package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 俄罗斯方块 PK 聊天广播
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TetrisChatVO {

    // 发送用户 ID
    private Long userId;

    // 消息类型
    private String messageType;

    // 文本内容
    private String content;

    // 表情包 ID
    private Long emojiId;

    // 表情包 URL
    private String emojiUrl;

    // 发送时间戳
    private Long sentAtMs;
}
