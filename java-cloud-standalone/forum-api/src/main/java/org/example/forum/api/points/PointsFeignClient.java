package org.example.forum.api.points;

import org.example.forum.api.economy.PointsInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 过渡：Feign 客户端仍留在 forum-api，方法签名继承 economy-api 契约；后续迁到消费方
@FeignClient(name = "forum-economy", contextId = "pointsFeignClient")
public interface PointsFeignClient extends PointsInternalApi {
}
