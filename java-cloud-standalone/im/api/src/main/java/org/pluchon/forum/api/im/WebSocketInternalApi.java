package org.pluchon.forum.api.im;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// IM WebSocket 内部契约：仅暴露状态查询与异步推送入口
public interface WebSocketInternalApi {

    @GetMapping("/websocket/internal/is-online")
    Boolean isOnline(@RequestParam("userId") Long userId);

    @PostMapping("/websocket/internal/push")
    void push(@RequestParam("userId") Long userId, @RequestParam("payload") String payload);
}
