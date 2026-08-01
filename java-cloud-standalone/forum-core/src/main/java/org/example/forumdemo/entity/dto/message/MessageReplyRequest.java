package org.example.forumdemo.entity.dto.message;

import lombok.Data;

/**
 * @author pluchon
 * @create 2026-03-10-17:50
 * 作者代码水平一般，难免难看，请见谅
 */
//站内信回复请求
@Data
public class MessageReplyRequest {
    //回复的接收者的ID
    private Long receiveId;
    private String content;
}
