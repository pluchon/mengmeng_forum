package org.pluchon.forum.entity.vo.message;

import lombok.Data;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

import java.util.Date;

// 私聊语音参与者
@Data
public class PrivateVoiceParticipantVO {

    // 用户信息
    private UserBriefVO user;

    // 本次连接ID
    private String connectionId;

    // 加入时间
    private Date joinedAt;
}
