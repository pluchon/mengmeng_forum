package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.economy.VipInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 过渡：消费方 Feign 客户端，契约来自 forum-economy-api
@FeignClient(name = "forum-economy", contextId = "vipInternalFeignClient")
public interface VipInternalFeignClient extends VipInternalApi {
}
