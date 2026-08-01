package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.economy.ShopEntitlementInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 内容域消费经济表情权益内部契约的客户端
@FeignClient(name = "forum-economy", contextId = "shopEntitlementInternalFeignClient")
public interface ShopEntitlementInternalFeignClient extends ShopEntitlementInternalApi {
}
