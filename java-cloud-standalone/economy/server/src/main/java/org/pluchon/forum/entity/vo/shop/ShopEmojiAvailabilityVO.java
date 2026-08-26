package org.pluchon.forum.entity.vo.shop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.api.economy.ShopEmojiAvailability;

// 商城表情实时可用性
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShopEmojiAvailabilityVO {

    private ShopEmojiAvailability status;
    private Long shopId;
}
