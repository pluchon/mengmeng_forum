package org.example.forumdemo.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameLeaderboardEntryVO {

    private Integer rank;

    private Long userId;

    private String nickname;

    private String avatarUrl;

    private Integer score;

    private Integer winCount;

    private Integer totalCount;

    private Integer winRate;
}
