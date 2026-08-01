package org.pluchon.forum.entity.vo.groupchat;

import lombok.Data;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

import java.util.Date;

// 群消息响应
@Data
public class GroupChatMessageVO {
    // 群消息 ID
    private Long id;
    // 群聊 ID
    private Long groupId;
    // 发送者信息，系统消息为空
    private UserBriefVO sender;
    // 消息类型
    private Byte messageType;
    // 消息内容
    private String content;
    // 回复的群消息 ID
    private Long replyMessageId;
    // 被回复消息发送者昵称快照
    private String replySenderName;
    // 被回复消息内容快照
    private String replyContent;
    // 消息状态
    private Byte status;
    // 是否当前登录用户发送
    private Boolean isOwner;
    // 创建时间
    private Date createTime;
    // 更新时间
    private Date updateTime;
}
