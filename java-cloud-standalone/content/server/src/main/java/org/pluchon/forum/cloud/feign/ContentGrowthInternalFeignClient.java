package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.economy.GrowthInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// content 域自有的成长状态校验客户端。
@FeignClient(name = "forum-economy", contextId = "contentGrowthInternalFeignClient")
public interface ContentGrowthInternalFeignClient extends GrowthInternalApi {
}
