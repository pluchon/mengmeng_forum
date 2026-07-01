package org.example.forumdemo.entity.vo.groupchat;

import lombok.Data;

import java.util.Date;

// 群聊会话响应
@Data
public class GroupChatSessionVO {
    // 群聊 ID
    private Long groupId;
    // 群名称
    private String name;
    // 群头像 URL
    private String avatarUrl;
    // 群简介
    private String intro;
    // 群类型
    private Byte groupType;
    // 群状态
    private Byte status;
    // 群主用户 ID
    private Long ownerUserId;
    // 当前成员数
    private Integer memberCount;
    // 成员上限
    private Integer memberLimit;
    // 最新消息摘要
    private String lastMessage;
    // 最新消息时间
    private Date lastMessageTime;
    // 未读消息数
    private Long unreadCount;
}
