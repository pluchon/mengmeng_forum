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

    // 每周限购次数，0 不限。AI 额度重置卡这类有真实成本的商品用它约束
    private Integer weeklyLimit;

    private Integer sortOrder;

    private Integer enabled;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
