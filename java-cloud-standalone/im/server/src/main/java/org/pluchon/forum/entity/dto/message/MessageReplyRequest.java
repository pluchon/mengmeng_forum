package org.pluchon.forum.entity.dto.message;

import lombok.Data;

// 作者代码水平一般，难免难看，请见谅
// 站内信回复请求
@Data
public class MessageReplyRequest {
    // 回复的接收者的ID
    private Long receiveId;
    private String content;
}
