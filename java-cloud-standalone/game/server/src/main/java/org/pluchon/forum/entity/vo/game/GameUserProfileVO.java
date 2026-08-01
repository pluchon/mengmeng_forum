package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 用户游戏资料摘要
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameUserProfileVO {

    // 用户 ID
    private Long userId;

    // 用户名
    private String username;

    // 昵称
    private String nickname;

    // 头像地址
    private String avatarUrl;

    // 游戏编码
    private String gameCode;

    // 天梯分
    private Integer score;

    // 当前段位展示
    private GameRankInfoVO rankInfo;

    // 当前段位名称
    private String rankName;

    // 距离下一小段还差多少分
    private Integer nextRankDistance;

    // 论坛积分余额
    private Integer forumPoints;

    // 总局数
    private Integer totalCount;

    // 胜局数
    private Integer winCount;

    // 负局数
    private Integer loseCount;

    // 平局数
    private Integer drawCount;

    // 胜率百分比，0-100
    private Integer winRate;

    // 当前状态
    private String currentStatus;

    // 当前房间 ID
    private String currentRoomId;
}
