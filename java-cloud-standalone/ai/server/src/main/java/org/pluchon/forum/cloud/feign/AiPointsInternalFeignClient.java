package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.economy.PointsInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// AI 服务私有的积分域客户端
@FeignClient(name = "forum-economy", contextId = "aiPointsInternalFeignClient")
public interface AiPointsInternalFeignClient extends PointsInternalApi {
}
