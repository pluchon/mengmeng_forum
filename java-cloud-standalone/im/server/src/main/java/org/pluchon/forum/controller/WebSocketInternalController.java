package org.pluchon.forum.controller;

import org.pluchon.forum.api.im.WebSocketInternalApi;
import org.pluchon.forum.common.utils.OnlineUserManageUtil;
import org.pluchon.forum.service.impl.websocket.WebSocketPushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

// IM WebSocket 内部接口实现
@RestController
public class WebSocketInternalController implements WebSocketInternalApi {

    @Autowired
    private OnlineUserManageUtil onlineUserManageUtil;

    @Autowired
    private WebSocketPushService webSocketPushService;

    @Override
    public Boolean isOnline(Long userId) {
        return onlineUserManageUtil.isOnline(userId);
    }

    @Override
    public void push(Long userId, String payload) {
        webSocketPushService.push(userId, payload);
    }
}
