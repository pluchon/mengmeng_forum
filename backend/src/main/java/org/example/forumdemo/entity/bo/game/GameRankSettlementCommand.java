package org.example.forumdemo.entity.bo.game;

import lombok.Data;

// 游戏排位结算命令
@Data
public class GameRankSettlementCommand {

    // 游戏编码
    private String gameCode;

    // 房间 ID
    private String roomId;

    // 玩家 A
    private Long playerAUserId;

    // 玩家 B
    private Long playerBUserId;

    // 胜方用户 ID，平局为空
    private Long winnerUserId;

    // 败方用户 ID，平局为空
    private Long loserUserId;

    // 结束原因
    private String endReason;

    // 是否达到排位有效门槛
    private Boolean effectiveForRank;
}
