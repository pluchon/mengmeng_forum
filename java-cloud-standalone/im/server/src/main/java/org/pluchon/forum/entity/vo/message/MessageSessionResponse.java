package org.pluchon.forum.entity.vo.message;

import lombok.Data;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

import java.util.Date;

// 作者代码水平一般，难免难看，请见谅
// 聊天消息列表
@Data
public class MessageSessionResponse {
    // 对方的用户信息
    private UserBriefVO user;
    // 最新的一条消息
    private String lastMessage;
    // 未读消息数量
    private Long unReadMessage;
    // 最新的消息的时间
    private Date lastMessageTime;

    // 置顶时刻；NULL 表示未置顶
    private Date pinnedAt;
}
