package org.pluchon.forum.entity.vo.groupchat;

import lombok.Data;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

import java.util.Date;
import java.util.List;

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
    // 图集图片
    private List<GroupChatAlbumImageVO> albumImages;
    // 回复的群消息 ID
    private Long replyMessageId;
    // 被回复消息发送者昵称快照
    private String replySenderName;
    // 被回复消息内容快照
    private String replyContent;
    // 消息状态
    private Byte status;
    // 文本审核未通过：仅发送方可见，气泡左侧红叹号
    private Boolean auditFailed;
    // 是否当前登录用户发送
    private Boolean isOwner;
    // 撤回截止时间
    private Date recallDeadline;
    // 创建时间
    private Date createTime;
    // 更新时间
    private Date updateTime;
}
