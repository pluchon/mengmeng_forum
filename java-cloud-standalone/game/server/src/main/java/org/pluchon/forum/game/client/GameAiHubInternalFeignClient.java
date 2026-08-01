package org.pluchon.forum.game.client;

import org.pluchon.forum.api.ai.AiHubInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 游戏服务调用 AI 域的本地客户端
@FeignClient(name = "forum-ai", contextId = "gameAiHubInternalFeignClient")
public interface GameAiHubInternalFeignClient extends AiHubInternalApi {
}
