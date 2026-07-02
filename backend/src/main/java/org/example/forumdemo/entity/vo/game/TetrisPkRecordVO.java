package org.example.forumdemo.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

// 俄罗斯方块 PK 历史记录
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TetrisPkRecordVO {

    // 记录 ID
    private Long id;

    // 房间 ID
    private String roomId;

    // 当前用户 ID
    private Long userId;

    // 对手用户 ID
    private Long opponentUserId;

    // 对手昵称
    private String opponentNickname;

    // 我方得分
    private Integer myScore;

    // 对手得分
    private Integer opponentScore;

    // 胜方用户 ID
    private Long winnerUserId;

    // 积分变动
    private Integer scoreDelta;

    // 胜方真实排位分变化
    private Integer winnerScoreDelta;

    // 败方真实排位分变化
    private Integer loserScoreDelta;

    // 结束原因
    private String endReason;

    // 开始时间
    private Date startedAt;

    // 结束时间
    private Date endedAt;
}
