package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.economy.ShopEntitlementInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// IM 服务私有的商城表情权益客户端
@FeignClient(name = "forum-economy", contextId = "imShopEntitlementInternalFeignClient")
public interface ImShopEntitlementInternalFeignClient extends ShopEntitlementInternalApi {
}
