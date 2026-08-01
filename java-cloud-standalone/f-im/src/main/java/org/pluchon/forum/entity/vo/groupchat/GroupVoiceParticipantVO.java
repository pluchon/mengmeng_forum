package org.pluchon.forum.entity.vo.groupchat;

import lombok.Data;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

import java.util.Date;

// 群语音席位成员
@Data
public class GroupVoiceParticipantVO {

    // 用户简要信息
    private UserBriefVO user;

    // 本次加入语音的连接 ID
    private String connectionId;

    // 加入时间
    private Date joinedAt;
}
