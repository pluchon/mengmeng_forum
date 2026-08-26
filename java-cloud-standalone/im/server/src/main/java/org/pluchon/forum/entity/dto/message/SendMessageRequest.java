package org.pluchon.forum.entity.dto.message;

import lombok.Data;

// 作者代码水平一般，难免难看，请见谅
// 发送站内信请求
@Data
public class SendMessageRequest {
    private Long receiveUserId;
    private String content;
}
