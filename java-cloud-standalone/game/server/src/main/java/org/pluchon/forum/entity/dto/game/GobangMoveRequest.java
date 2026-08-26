package org.pluchon.forum.entity.dto.game;

import lombok.Data;

// 五子棋落子请求
@Data
public class GobangMoveRequest {

    // 棋盘行号，0 14
    private Integer row;

    // 棋盘列号，0 14
    private Integer col;
}
