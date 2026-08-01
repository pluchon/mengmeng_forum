package org.pluchon.forum.common.websocket.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 游戏 WebSocket 响应外壳，所有游戏实时消息统一使用该结构
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameWsResponse<T> {

    // 消息类型
    private String type;

    // 是否成功
    private boolean ok;

    // 前端请求 ID，服务端主动推送时可为空
    private String requestId;

    // 提示信息
    private String message;

    // 业务数据
    private T data;

    public static <T> GameWsResponse<T> ok(String type, String requestId, T data) {
        return new GameWsResponse<>(type, true, requestId, "", data);
    }

    public static <T> GameWsResponse<T> fail(String type, String requestId, String message) {
        return new GameWsResponse<>(type, false, requestId, message, null);
    }
}
