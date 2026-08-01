package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.economy.PointsInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// game 域自有的积分内部调用客户端。
@FeignClient(name = "forum-economy", contextId = "gamePointsInternalFeignClient")
public interface GamePointsInternalFeignClient extends PointsInternalApi {
}
