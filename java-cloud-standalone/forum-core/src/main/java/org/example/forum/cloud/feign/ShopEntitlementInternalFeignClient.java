package org.example.forum.cloud.feign;

import org.example.forum.api.economy.ShopEntitlementInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 过渡：消费方 Feign，契约来自 forum-economy-api
@FeignClient(name = "forum-economy", contextId = "shopEntitlementInternalFeignClient")
public interface ShopEntitlementInternalFeignClient extends ShopEntitlementInternalApi {
}
