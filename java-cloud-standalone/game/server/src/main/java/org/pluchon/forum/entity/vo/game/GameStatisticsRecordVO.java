package org.pluchon.forum.entity.vo.game;

import lombok.Data;

import java.util.Date;

// 统一对局统计记录
@Data
public class GameStatisticsRecordVO {

    // 来源记录 ID
    private Long sourceRecordId;

    // 游戏编码
    private String gameCode;

    // 房间 ID，单人游戏为空
    private String roomId;

    // WIN / LOSE / DRAW / FINISHED
    private String resultCode;

    // 结束原因
    private String endReason;

    // 当前用户本局段位分变化
    private Integer scoreDelta;

    // 本局游戏得分
    private Integer score;

    // 俄罗斯方块等级
    private Integer level;

    // 俄罗斯方块消行数
    private Integer linesCleared;

    // 开始时间
    private Date startedAt;

    // 结束时间
    private Date endedAt;

    // 对手用户 ID
    private Long opponentUserId;

    // 对手真实昵称
    private String opponentNickname;

    // 对手真实头像
    private String opponentAvatarUrl;

    // 对手真实得分 PK/对战比分
    private Integer opponentScore;

    // 玩家执棋/阵营角色 BLACK/WHITE/X/O
    private String playerRole;
}
