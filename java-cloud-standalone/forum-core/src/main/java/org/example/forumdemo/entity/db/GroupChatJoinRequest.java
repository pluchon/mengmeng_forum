package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 群加入申请与邀请记录
@Data
@TableName("group_chat_join_request")
public class GroupChatJoinRequest {

    // 申请 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 群聊 ID
    private Long groupId;

    // 目标用户 ID
    private Long targetUserId;

    // 发起人用户 ID
    private Long initiatorUserId;

    // 群主用户 ID
    private Long ownerUserId;

    // 请求类型: 0申请加群 1邀请入群
    private Byte requestType;

    // 处理状态: 0待处理 1已同意 2已拒绝
    private Byte status;

    // 群主是否已查看: 0未读 1已读
    private Byte ownerReadState;

    // 处理人用户 ID
    private Long handledByUserId;

    // 处理时间
    private Date handleTime;

    // 是否删除: 0否 1是
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
