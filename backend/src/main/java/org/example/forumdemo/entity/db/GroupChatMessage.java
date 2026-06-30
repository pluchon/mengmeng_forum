package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 群聊消息表
@Data
@TableName("group_chat_message")
public class GroupChatMessage {

    // 群消息 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 群聊 ID
    private Long groupId;

    // 发送者用户 ID，系统消息为空
    private Long senderUserId;

    // 消息类型: 0文本 1表情 9系统
    private Byte messageType;

    // 消息内容
    private String content;

    // 状态: 0正常 1举报隐藏 2删除
    private Byte status;

    // 是否删除: 0否 1是
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
