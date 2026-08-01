package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.economy.PointsInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// Feign 客户端：方法签名继承 economy-api 契约，收口到 forum-economy
@FeignClient(name = "forum-economy", contextId = "pointsFeignClient")
public interface PointsFeignClient extends PointsInternalApi {
}
