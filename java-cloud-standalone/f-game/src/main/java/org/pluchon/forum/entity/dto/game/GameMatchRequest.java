package org.pluchon.forum.entity.dto.game;

import lombok.Data;

// 五子棋匹配 WebSocket 请求，type 已在消息外壳中表达，本对象预留后续匹配模式
@Data
public class GameMatchRequest {

    // 匹配模式：RANKED 排位，后续可扩展 FRIEND
    private String mode;
}
