package org.pluchon.forum.service.remote;

import org.pluchon.forum.api.economy.ShopEmojiAvailability;
import org.pluchon.forum.cloud.feign.ImShopEntitlementInternalFeignClient;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// IM 发送商城表情前的实时权益校验
@Service
public class ImShopEmojiAvailabilityService {

    @Autowired
    private ImShopEntitlementInternalFeignClient shopEntitlementInternalFeignClient;

    public void assertAvailable(Long userId, Long shopId, String url) {
        if (shopId == null || shopId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_EMOJI_DELETED));
        }
        ShopEmojiAvailability status = shopEntitlementInternalFeignClient
                .checkShopEmojiAvailability(userId, shopId, url);
        if (ShopEmojiAvailability.AVAILABLE.equals(status)) {
            return;
        }
        if (ShopEmojiAvailability.SERIES_OFFLINE.equals(status)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_SERIES_OFFLINE));
        }
        if (ShopEmojiAvailability.NOT_OWNED.equals(status)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_EMOJI_NOT_OWNED));
        }
        throw new ApplicationException(Result.fail(ResultCode.FAILED_SHOP_EMOJI_DELETED));
    }
}
