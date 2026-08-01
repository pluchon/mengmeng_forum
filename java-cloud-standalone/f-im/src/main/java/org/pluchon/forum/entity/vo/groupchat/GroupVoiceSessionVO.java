package org.pluchon.forum.entity.vo.groupchat;

import lombok.Data;

import java.util.Date;
import java.util.List;

// 群语音房状态
@Data
public class GroupVoiceSessionVO {

    // 群聊 ID
    private Long groupId;

    // 是否有语音聊天
    private Boolean active;

    // 房间版本
    private Long roomVersion;

    // 发起人用户 ID
    private Long initiatorUserId;

    // 当前人数
    private Integer memberCount;

    // 最大席位数
    private Integer maxSeats;

    // 当前用户是否已加入
    private Boolean currentUserJoined;

    // 当前用户本次加入语音的连接 ID
    private String currentConnectionId;

    // 当前用户是否可发起
    private Boolean currentUserManager;

    // 开始时间
    private Date startedAt;

    // 席位列表
    private List<GroupVoiceParticipantVO> participants;
}
