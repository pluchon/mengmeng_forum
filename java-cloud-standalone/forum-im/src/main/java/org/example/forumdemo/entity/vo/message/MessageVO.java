package org.example.forumdemo.entity.vo.message;

import lombok.Data;

import java.util.Date;

// 私信消息对外展示
@Data
public class MessageVO {

    private Long id;
    private Long postUserId;
    private Long receiveUserId;
    private Byte messageType;
    private String content;
    private String mediaUrl;
    private String mediaMime;
    private Long mediaSize;
    private Integer mediaWidth;
    private Integer mediaHeight;
    private Byte state;
    private Date createTime;
    private Date updateTime;
}
