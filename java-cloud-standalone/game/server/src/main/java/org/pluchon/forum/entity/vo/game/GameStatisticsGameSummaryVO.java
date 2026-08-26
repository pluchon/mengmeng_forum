package org.pluchon.forum.entity.vo.game;

import lombok.Data;

// 单个游戏的统计摘要
@Data
public class GameStatisticsGameSummaryVO {

    // 游戏编码
    private String gameCode;

    // 当前段位分
    private Integer rankScore;

    // 总对局数
    private Integer totalCount;

    // 胜局数
    private Integer winCount;

    // 负局数
    private Integer loseCount;

    // 平局数
    private Integer drawCount;

    // 胜率百分比
    private Double winRate;
}
