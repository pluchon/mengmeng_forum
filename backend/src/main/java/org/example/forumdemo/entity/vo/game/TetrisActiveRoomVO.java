package org.example.forumdemo.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

// 俄罗斯方块 PK 可观战房间摘要
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TetrisActiveRoomVO {

    // 房间 ID
    private String roomId;

    // 玩家1用户 ID
    private Long player1UserId;

    // 玩家2用户 ID
    private Long player2UserId;

    // 红方用户 ID
    private Long redUserId;

    // 红方昵称
    private String redNickname;

    // 蓝方用户 ID
    private Long blueUserId;

    // 蓝方昵称
    private String blueNickname;

    // 红方分数
    private Integer redScore;

    // 蓝方分数
    private Integer blueScore;

    // 开始时间
    private Date startedAt;
}
