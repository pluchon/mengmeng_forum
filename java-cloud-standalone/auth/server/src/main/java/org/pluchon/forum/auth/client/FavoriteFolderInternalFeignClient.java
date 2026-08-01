package org.pluchon.forum.auth.client;

import org.pluchon.forum.api.content.FavoriteFolderInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 认证域消费内容域默认收藏夹内部契约
@FeignClient(name = "forum-content", contextId = "authFavoriteFolderInternalFeignClient")
public interface FavoriteFolderInternalFeignClient extends FavoriteFolderInternalApi {
}
