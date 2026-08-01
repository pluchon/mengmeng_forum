package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

// 游戏对局记录响应
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameMatchRecordVO {

    // 对局记录 ID
    private Long id;

    // 游戏编码
    private String gameCode;

    // 房间 ID
    private String roomId;

    // 黑方用户 ID
    private Long blackUserId;

    // 白方用户 ID
    private Long whiteUserId;

    // 胜方用户 ID
    private Long winnerUserId;

    // 负方用户 ID
    private Long loserUserId;

    // 结束原因
    private String endReason;

    // 积分变化
    private Integer scoreDelta;

    // 胜方真实排位分变化
    private Integer winnerScoreDelta;

    // 败方真实排位分变化
    private Integer loserScoreDelta;

    // 当前查看用户本局排位分变化
    private Integer viewerScoreDelta;

    // 开始时间
    private Date startedAt;

    // 结束时间
    private Date endedAt;
}
