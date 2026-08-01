package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.auth.UserInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 内容服务私有的认证域用户内部客户端
@FeignClient(name = "forum-auth", contextId = "contentUserInternalFeignClient")
public interface ContentUserInternalFeignClient extends UserInternalApi {
}
