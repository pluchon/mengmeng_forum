package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 群聊举报表
@Data
@TableName("group_chat_report")
public class GroupChatReport {

    // 举报记录 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 群聊 ID
    private Long groupId;

    // 群消息 ID
    private Long messageId;

    // 举报人用户 ID
    private Long reporterUserId;

    // 被举报用户 ID
    private Long targetUserId;

    // 举报原因
    private String reason;

    // 处理状态: 0待处理 1已处理 2驳回
    private Byte status;

    // 是否删除: 0否 1是
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
