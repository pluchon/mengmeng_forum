package org.pluchon.forum.im.client;

import org.pluchon.forum.api.ai.AiHubInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 即时通讯服务调用 AI 域的本地客户端
@FeignClient(name = "forum-ai", contextId = "imAiHubInternalFeignClient")
public interface ImAiHubInternalFeignClient extends AiHubInternalApi {
}
