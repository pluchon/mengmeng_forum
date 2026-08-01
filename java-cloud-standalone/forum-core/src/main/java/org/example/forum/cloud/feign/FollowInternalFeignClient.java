package org.example.forum.cloud.feign;

import org.example.forum.api.auth.FollowInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 过渡：消费方 Feign 客户端，契约来自 forum-auth-api
@FeignClient(name = "forum-auth", contextId = "followInternalFeignClient")
public interface FollowInternalFeignClient extends FollowInternalApi {
}
