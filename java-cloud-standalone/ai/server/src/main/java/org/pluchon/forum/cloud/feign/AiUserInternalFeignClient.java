package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.auth.UserInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// AI 服务私有的 auth 用户读取客户端
@FeignClient(name = "forum-auth", contextId = "aiUserInternalFeignClient")
public interface AiUserInternalFeignClient extends UserInternalApi {
}
