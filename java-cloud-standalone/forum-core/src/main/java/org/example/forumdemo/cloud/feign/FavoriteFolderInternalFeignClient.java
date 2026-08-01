package org.example.forumdemo.cloud.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

// 收藏夹内部接口（content 域）；auth 注册后创建默认夹
@FeignClient(name = "forum-content", contextId = "favoriteFolderInternalFeignClient")
public interface FavoriteFolderInternalFeignClient {

    @PostMapping("/favorite/internal/{userId}/ensure-default-folder")
    Long ensureDefaultFolder(@PathVariable("userId") Long userId);
}
