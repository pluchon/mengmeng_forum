package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 游戏中心推荐对局分类
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameCategoryVO {

    // all | pvp | solo
    private String code;

    private String label;
}
