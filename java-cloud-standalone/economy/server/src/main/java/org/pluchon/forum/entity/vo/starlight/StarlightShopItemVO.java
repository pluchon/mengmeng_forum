package org.pluchon.forum.entity.vo.starlight;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "萌星辉商城商品")
public class StarlightShopItemVO {

    private Long id;

    private String name;

    private String category;

    private String tag;

    private Integer priceStarlight;

    private String rewardType;

    private Integer rewardValue;

    // 1 不限量
    private Integer stockRemaining;

    // 0 不限
    private Integer dailyLimit;

    private Integer weeklyLimit;

    private Integer sortOrder;
}
