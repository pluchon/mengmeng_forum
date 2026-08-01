package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.ai.AiUsageInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 经济域消费 AI 用量内部契约的客户端
@FeignClient(name = "forum-ai", contextId = "aiUsageInternalFeignClient")
public interface AiUsageInternalFeignClient extends AiUsageInternalApi {
}
