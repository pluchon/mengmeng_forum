package org.example.forumdemo.entity.dto.message;

import lombok.Data;

/**
 * @author pluchon
 * @create 2026-03-10-15:06
 * 作者代码水平一般，难免难看，请见谅
 */
//发送站内信请求
@Data
public class SendMessageRequest {
    private Long receiveUserId;
    private String content;
}
