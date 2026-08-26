package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 萌星辉商城商品，对应 starlight_shop_item
@Data
@TableName("starlight_shop_item")
public class StarlightShopItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    // HOT / LIMITED / COSMETIC / UTILITY
    private String category;

    private String tag;

    private Integer priceStarlight;

    // 本阶段仅 VIP_DAYS
    private String rewardType;

    private Integer rewardValue;

    // 1 不限量
    private Integer stockRemaining;

    // 0 不限
    private Integer dailyLimit;

    private Integer sortOrder;

    private Integer enabled;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
