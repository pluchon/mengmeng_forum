package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.auth.FollowInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 过渡：消费方 Feign 客户端，契约来自 forum-auth-api
@FeignClient(name = "forum-auth", contextId = "followInternalFeignClient")
public interface FollowInternalFeignClient extends FollowInternalApi {
}
