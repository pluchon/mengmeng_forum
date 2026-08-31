package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户背包。
 *
 * <p>兑换与中奖的「卡片类」奖品先落在这里，由用户择时使用；
 * 积分与萌星辉是流水型货币，仍然即时到账，不进背包。
 */
@Data
@TableName("user_bag_item")
public class UserBagItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** EXCHANGE 兑换 / LOTTERY 抽奖 */
    private String source;

    /** 来源单据 ID：兑换记录或抽奖记录 */
    private Long sourceRefId;

    private String itemName;

    /** LOTTERY_VOUCHER / MAKEUP_CARD / QUOTA_RESET / VIP_DAYS / GOODS */
    private String rewardType;

    private Integer rewardValue;

    /** 会员档位 1 PRO 2 MAX */
    private Byte vipTier;

    /** 0 未使用 1 已使用 2 待发放（实物） */
    private Integer useStatus;

    private Date useTime;

    private String grantSummary;

    private String idempotencyKey;

    private Byte deleteState;

    private Date createTime;

    private Date updateTime;
}
