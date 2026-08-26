package org.pluchon.forum.entity.vo.groupchat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

// 群聊文本搜索命中的逻辑会话
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GroupChatSessionSearchResponse {

    private Long groupId;

    private Long matchedMessageId;

    private String matchedContent;

    private Date matchedMessageTime;
}
