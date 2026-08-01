package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.economy.VipInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// IM 服务私有的会员权益读取客户端
@FeignClient(name = "forum-economy", contextId = "imVipInternalFeignClient")
public interface ImVipInternalFeignClient extends VipInternalApi {
}
