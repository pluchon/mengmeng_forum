package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.UserInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// IM 服务私有的认证用户内部客户端
@FeignClient(name = "forum-auth", contextId = "imUserInternalFeignClient")
public interface ImUserInternalFeignClient extends UserInternalApi {
}
