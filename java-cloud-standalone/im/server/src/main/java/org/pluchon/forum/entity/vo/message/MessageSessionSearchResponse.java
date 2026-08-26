package org.pluchon.forum.entity.vo.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

// 私信文本搜索命中的逻辑会话
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageSessionSearchResponse {

    // 私信对方用户 ID
    private Long peerUserId;

    // 最新命中的消息 ID
    private Long matchedMessageId;

    // 最新命中的文本内容
    private String matchedContent;

    // 最新命中的消息时间
    private Date matchedMessageTime;
}
