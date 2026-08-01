package org.pluchon.forum.entity.vo.groupchat;

import lombok.Data;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

import java.util.Date;

// 群加入申请响应
@Data
public class GroupChatJoinRequestVO {
    // 申请 ID
    private Long id;
    // 群聊详情
    private GroupChatDetailVO group;
    // 目标用户
    private UserBriefVO targetUser;
    // 发起人
    private UserBriefVO initiatorUser;
    // 请求类型
    private Byte requestType;
    // 请求状态
    private Byte status;
    // 群主查看状态
    private Byte ownerReadState;
    // 目标用户是否已入群
    private Boolean targetJoined;
    // 创建时间
    private Date createTime;
    // 处理时间
    private Date handleTime;
}
