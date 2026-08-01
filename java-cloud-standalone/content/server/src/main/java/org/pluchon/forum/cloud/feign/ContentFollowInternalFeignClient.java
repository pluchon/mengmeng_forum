package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.auth.FollowInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 内容服务私有的关注内部客户端
@FeignClient(name = "forum-auth", contextId = "contentFollowInternalFeignClient")
public interface ContentFollowInternalFeignClient extends FollowInternalApi {
}
