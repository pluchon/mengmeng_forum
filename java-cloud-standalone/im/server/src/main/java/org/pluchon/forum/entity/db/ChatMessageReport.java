package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 私信与群消息举报记录
@Data
@TableName("chat_message_report")
public class ChatMessageReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reporterUserId;
    private Byte conversationType;
    private Long messageId;
    private String contentHash;
    private String reason;
    private String taskId;
    private Byte resultStatus;
    private String resultMessage;
    private Date createTime;
    private Date updateTime;
    private Byte deleteState;
}
