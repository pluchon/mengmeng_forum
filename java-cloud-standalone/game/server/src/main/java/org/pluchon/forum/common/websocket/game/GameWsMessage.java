package org.pluchon.forum.common.websocket.game;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

// 游戏 WebSocket 请求外壳，data 由各业务 handler 转换成独立 DTO
@Data
public class GameWsMessage {

    // 消息类型
    private String type;

    // 前端请求 ID，用于排查重复点击和响应对应关系
    private String requestId;

    // 业务数据载荷
    private JsonNode data;
}
