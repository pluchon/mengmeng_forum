package org.pluchon.forum.api.economy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

// 商店 entitlement 内部契约 纯 API，无 @FeignClient
public interface ShopEntitlementInternalApi {

    // 校验用户是否拥有指定商店表情，且 url 属于该商店上架条目
    @GetMapping("/shop/internal/{userId}/owned-emoji")
    Boolean ownsShopEmojiUrl(
            @PathVariable("userId") Long userId,
            @RequestParam("shopId") Long shopId,
            @RequestParam("url") String url
    );

    // 校验发送商城表情时的权益和实时上下架状态
    @GetMapping("/shop/internal/{userId}/emoji-availability")
    ShopEmojiAvailability checkShopEmojiAvailability(
            @PathVariable("userId") Long userId,
            @RequestParam("shopId") Long shopId,
            @RequestParam("url") String url
    );
}
