package org.example.forumdemo.controller;

import org.example.forum.api.economy.ShopEntitlementInternalApi;
import org.example.forumdemo.service.interfaces.shop.EmojiShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 商店 entitlement 内部接口：契约路径已是 /shop/internal/**，勿再叠加 @RequestMapping("/shop")
@RestController
public class ShopEntitlementInternalController implements ShopEntitlementInternalApi {

    @Autowired
    private EmojiShopService emojiShopService;

    @Override
    public Boolean ownsShopEmojiUrl(
            @PathVariable("userId") Long userId,
            @RequestParam("shopId") Long shopId,
            @RequestParam("url") String url) {
        return emojiShopService.ownsShopEmojiUrl(userId, shopId, url);
    }
}
