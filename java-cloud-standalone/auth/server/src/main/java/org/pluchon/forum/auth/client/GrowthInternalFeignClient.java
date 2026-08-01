package org.pluchon.forum.auth.client;

import org.pluchon.forum.api.economy.GrowthInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 认证域消费经济域成长内部契约
@FeignClient(name = "forum-economy", contextId = "authGrowthInternalFeignClient")
public interface GrowthInternalFeignClient extends GrowthInternalApi {
}
