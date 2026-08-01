package org.pluchon.forum.auth.client;

import org.pluchon.forum.api.ai.AiHubInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 认证服务调用 AI 域的本地客户端
@FeignClient(name = "forum-ai", contextId = "authAiHubInternalFeignClient")
public interface AuthAiHubInternalFeignClient extends AiHubInternalApi {
}
