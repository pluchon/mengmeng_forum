package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.auth.UserInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// game 域自有的用户内部查询客户端。
@FeignClient(name = "forum-auth", contextId = "gameUserInternalFeignClient")
public interface GameUserInternalFeignClient extends UserInternalApi {
}
