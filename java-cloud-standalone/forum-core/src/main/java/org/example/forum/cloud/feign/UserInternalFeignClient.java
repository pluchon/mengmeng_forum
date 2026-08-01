package org.example.forum.cloud.feign;

import org.example.forum.api.auth.UserInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 过渡：消费方 Feign 客户端，契约来自 forum-auth-api（无 Entity）
@FeignClient(name = "forum-auth", contextId = "userInternalFeignClient")
public interface UserInternalFeignClient extends UserInternalApi {
}
