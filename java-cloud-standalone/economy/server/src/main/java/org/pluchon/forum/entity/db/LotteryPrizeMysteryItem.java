package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Date;

@Data
@TableName("lottery_prize_mystery_item")
public class LotteryPrizeMysteryItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("prize_id")
    private Long prizeId;

    @TableField("item_type")
    private Byte itemType;

    @TableField("item_value")
    private Integer itemValue;

    // VIP天奖项的发放档位 1PRO 2MAX 空按PRO
    @TableField("vip_tier")
    private Byte vipTier;

    private Integer weight;

    // 剩余库存，-1 无限
    private Integer stockRemaining;

    @JsonIgnore
    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
