package org.pluchon.forum.entity.vo.message;

import lombok.Data;

import java.util.Date;
import java.util.List;

// 私聊语音会话
@Data
public class PrivateVoiceSessionVO {

    // 私聊语音会话ID
    private String sessionId;

    // 对方用户ID
    private Long peerUserId;

    // 是否有正在进行的语音
    private Boolean active;

    // 当前用户是否已加入
    private Boolean currentUserJoined;

    // 当前用户是否是发起者
    private Boolean currentUserInitiator;

    // 发起者用户ID
    private Long initiatorUserId;

    // 房间版本
    private Long roomVersion;

    // 当前用户连接ID
    private String currentConnectionId;

    // 当前人数
    private Integer memberCount;

    // 最大席位
    private Integer maxSeats;

    // 发起时间
    private Date startedAt;

    // 参与者
    private List<PrivateVoiceParticipantVO> participants;
}
