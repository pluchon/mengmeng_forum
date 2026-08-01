package org.pluchon.forum.economy.client;

import org.pluchon.forum.api.ai.AiHubInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 经济服务调用 AI 域的本地客户端
@FeignClient(name = "forum-ai", contextId = "economyAiHubInternalFeignClient")
public interface EconomyAiHubInternalFeignClient extends AiHubInternalApi {
}
