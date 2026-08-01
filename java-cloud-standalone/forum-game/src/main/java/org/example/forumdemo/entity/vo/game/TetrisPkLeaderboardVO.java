package org.example.forumdemo.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 俄罗斯方块 PK 排行榜项
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TetrisPkLeaderboardVO {

    private Long userId;

    private String username;

    private String nickname;

    private String avatarUrl;

    private Integer winRate;

    private Integer bestScore;

    private Integer totalCount;

    private Integer winCount;
}
