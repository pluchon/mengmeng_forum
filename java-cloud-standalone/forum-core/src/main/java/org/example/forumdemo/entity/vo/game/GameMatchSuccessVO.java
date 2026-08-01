package org.example.forumdemo.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 五子棋匹配成功响应载荷
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameMatchSuccessVO {

    // 房间 ID
    private String roomId;

    // 当前用户 ID
    private Long thisUserId;

    // 对手用户 ID
    private Long opponentUserId;
}
