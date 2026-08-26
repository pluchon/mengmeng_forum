package org.pluchon.forum.api.content;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

// 收藏夹域内部契约 纯 API，无 @FeignClient；注册等跨域场景确保默认收藏夹
public interface FavoriteFolderInternalApi {

    @PostMapping("/favorite/internal/{userId}/ensure-default-folder")
    Long ensureDefaultFolder(@PathVariable("userId") Long userId);
}
