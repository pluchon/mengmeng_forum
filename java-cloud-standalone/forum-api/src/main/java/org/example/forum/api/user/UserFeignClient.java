package org.example.forum.api.user;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// 用户域内部读接口（供其他服务通过 OpenFeign + LoadBalancer 调用）
@FeignClient(name = "forum-auth", contextId = "userFeignClient")
public interface UserFeignClient {

    @GetMapping("/user/internal/{userId}/exists")
    Boolean existsById(@PathVariable("userId") Long userId);
}
