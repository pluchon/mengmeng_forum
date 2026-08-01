package org.pluchon.forum.content.client;

import org.pluchon.forum.api.ai.AiHubInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 内容服务调用 AI 域的本地客户端
@FeignClient(name = "forum-ai", contextId = "contentAiHubInternalFeignClient")
public interface ContentAiHubInternalFeignClient extends AiHubInternalApi {
}
