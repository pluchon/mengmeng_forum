package org.example.forumdemo.entity.dto.game;

import lombok.Data;

// 俄罗斯方块 PK 房间输入请求
@Data
public class TetrisInputRequest {

    // 操作类型：left/right/down/rotate/space/hold
    private String action;
}
