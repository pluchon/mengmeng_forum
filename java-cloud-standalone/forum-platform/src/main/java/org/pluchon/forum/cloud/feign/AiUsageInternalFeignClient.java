package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.ai.AiUsageInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 过渡：消费方 Feign，契约来自 forum-ai-api
@FeignClient(name = "forum-ai", contextId = "aiUsageInternalFeignClient")
public interface AiUsageInternalFeignClient extends AiUsageInternalApi {
}
