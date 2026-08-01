package org.pluchon.forum.entity.vo.groupchat;

import lombok.Data;

import java.util.Date;

// 群聊详情响应
@Data
public class GroupChatDetailVO {
    // 群聊 ID
    private Long id;
    // 群主用户 ID
    private Long ownerUserId;
    // 群名称
    private String name;
    // 群头像 URL
    private String avatarUrl;
    // 群简介
    private String intro;
    // 群类型: 0公开 1私有
    private Byte groupType;
    // 成员上限
    private Integer memberLimit;
    // 当前成员数
    private Integer memberCount;
    // 群状态
    private Byte status;
    // 创建时间
    private Date createTime;
    // 更新时间
    private Date updateTime;
    // 当前用户是否已加入
    private Boolean currentUserJoined;
    // 当前用户加群申请状态
    private Byte currentUserRequestStatus;
    // 当前用户加群申请 ID
    private Long currentUserRequestId;
}
