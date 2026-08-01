package org.pluchon.forum.auth.client;

import org.pluchon.forum.api.economy.PointsInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 认证域消费经济域积分内部契约
@FeignClient(name = "forum-economy", contextId = "authPointsInternalFeignClient")
public interface PointsInternalFeignClient extends PointsInternalApi {
}
