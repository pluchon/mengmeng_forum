package org.pluchon.forum.entity.vo.game;

import lombok.Data;

import java.util.List;

// 游戏中心统一统计摘要
@Data
public class GameStatisticsSummaryVO {

    // 全部对局数
    private Integer totalCount;

    // 全部胜局数
    private Integer winCount;

    // 全部负局数
    private Integer loseCount;

    // 分游戏摘要
    private List<GameStatisticsGameSummaryVO> games;
}
