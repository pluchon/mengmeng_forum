package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

// 俄罗斯方块历史记录
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TetrisRecordVO {

    // 记录 ID
    private Long id;

    // 本局分数
    private Integer score;

    // 结束时等级
    private Integer level;

    // 总消行数
    private Integer linesCleared;

    // 局时长毫秒
    private Long durationMs;

    // 论坛积分奖励
    private Integer forumPointsAwarded;

    // 结束时间
    private Date endedAt;
}
