package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

// 五子棋可观战房间摘要
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GobangActiveRoomVO {

    // 房间 ID
    private String roomId;

    // 黑方用户 ID
    private Long blackUserId;

    // 黑方昵称
    private String blackNickname;

    // 白方用户 ID
    private Long whiteUserId;

    // 白方昵称
    private String whiteNickname;

    // 当前回合用户 ID
    private Long currentTurnUserId;

    // 是否 AI 对局
    private Boolean aiRoom;

    // 开始时间
    private Date startedAt;
}
