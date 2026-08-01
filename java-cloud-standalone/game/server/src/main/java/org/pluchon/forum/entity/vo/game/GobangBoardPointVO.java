package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 五子棋棋盘坐标
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GobangBoardPointVO {

    // 行号，0 开始
    private Integer row;

    // 列号，0 开始
    private Integer col;
}
