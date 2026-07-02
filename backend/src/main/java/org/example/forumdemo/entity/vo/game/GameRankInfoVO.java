package org.example.forumdemo.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 游戏排位展示信息
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameRankInfoVO {

    // 段位名称，例如黄金棋士 II
    private String rankName;

    // 大段名称，例如黄金
    private String majorName;

    // 小段名称，例如 II
    private String tierName;

    // 当前小段起始分
    private Integer rankMinScore;

    // 当前小段结束分，大师段为空
    private Integer rankMaxScore;

    // 下一小段起始分，大师段为空
    private Integer nextRankScore;

    // 距离下一小段还差多少分
    private Integer nextRankDistance;

    // 当前小段进度百分比
    private Integer progressPercent;
}
