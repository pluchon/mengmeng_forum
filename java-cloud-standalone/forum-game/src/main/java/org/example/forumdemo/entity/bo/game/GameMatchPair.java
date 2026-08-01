package org.example.forumdemo.entity.bo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 游戏匹配成功的两个用户
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameMatchPair {

    // 玩家 A
    private Long userIdA;

    // 玩家 B
    private Long userIdB;
}
