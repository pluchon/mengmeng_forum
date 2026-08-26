package org.pluchon.forum.cloud;

import org.pluchon.forum.api.ai.MascotPreferenceInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 认证域消费 AI 看板娘偏好内部契约的客户端
@FeignClient(name = "forum-ai", contextId = "mascotPreferenceInternalFeignClient")
public interface MascotPreferenceInternalFeignClient extends MascotPreferenceInternalApi {
}
