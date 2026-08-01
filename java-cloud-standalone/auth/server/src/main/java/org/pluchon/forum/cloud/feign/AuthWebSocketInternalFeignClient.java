package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.im.WebSocketInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 认证服务私有的 IM WebSocket 内部客户端
@FeignClient(name = "forum-im", contextId = "authWebSocketInternalFeignClient")
public interface AuthWebSocketInternalFeignClient extends WebSocketInternalApi {
}
