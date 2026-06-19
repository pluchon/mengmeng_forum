package org.example.forumdemo.entity.dto.game;

import lombok.Data;

// 房间动作请求，认输等不需要坐标的动作使用
@Data
public class GameRoomActionRequest {

    // 动作原因或备注，当前可为空
    private String reason;
}
