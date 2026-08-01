package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.auth.UserInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// content 域自有的认证用户读取客户端。
@FeignClient(name = "forum-auth", contextId = "contentAuthSnapshotInternalFeignClient")
public interface AuthSnapshotInternalFeignClient extends UserInternalApi {
}
