package org.example.forumdemo.cloud.feign;

import org.example.forum.api.content.FavoriteFolderInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 过渡：消费方 Feign 客户端，契约来自 forum-content-api
@FeignClient(name = "forum-content", contextId = "favoriteFolderInternalFeignClient")
public interface FavoriteFolderInternalFeignClient extends FavoriteFolderInternalApi {
}
