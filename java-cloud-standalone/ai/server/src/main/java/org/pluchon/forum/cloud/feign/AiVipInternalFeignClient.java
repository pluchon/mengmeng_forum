package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.economy.VipInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// AI 服务私有的 economy 会员读取客户端
@FeignClient(name = "forum-economy", contextId = "aiVipInternalFeignClient")
public interface AiVipInternalFeignClient extends VipInternalApi {
}
