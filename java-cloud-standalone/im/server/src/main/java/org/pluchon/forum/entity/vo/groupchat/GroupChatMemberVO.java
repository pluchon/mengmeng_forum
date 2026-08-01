package org.pluchon.forum.entity.vo.groupchat;

import lombok.Data;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

import java.util.Date;

// 群成员响应
@Data
public class GroupChatMemberVO {
    // 成员记录 ID
    private Long id;
    // 群聊 ID
    private Long groupId;
    // 用户简要信息
    private UserBriefVO user;
    // 群角色
    private Byte role;
    // 群内备注昵称
    private String remarkName;
    // 提醒模式
    private Byte notifyMode;
    // 禁言截止时间
    private Date muteUntil;
    // 最后已读消息 ID
    private Long lastReadMessageId;
    // 加入时间
    private Date joinTime;
    // 成员状态
    private Byte status;
}
