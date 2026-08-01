package org.example.forumdemo.cloud.feign;

import org.example.forum.api.economy.GrowthInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 过渡：消费方 Feign 客户端，契约来自 forum-economy-api
@FeignClient(name = "forum-economy", contextId = "growthInternalFeignClient")
public interface GrowthInternalFeignClient extends GrowthInternalApi {
}
