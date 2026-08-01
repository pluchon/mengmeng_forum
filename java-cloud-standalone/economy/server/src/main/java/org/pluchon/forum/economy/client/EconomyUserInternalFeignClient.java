package org.pluchon.forum.economy.client;

import org.pluchon.forum.api.auth.UserInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 经济服务私有的认证用户读取客户端
@FeignClient(name = "forum-auth", contextId = "economyUserInternalFeignClient")
public interface EconomyUserInternalFeignClient extends UserInternalApi {
}
