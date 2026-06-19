package org.example.forumdemo.entity.vo.lottery;

import lombok.Data;

/**
 * 单次加权抽签用的奖池行 (活动奖品关联 JOIN 奖品定义).
 */
@Data
public class LotteryPrizePoolRow {

    private Long activityPrizeId;

    private Long prizeId;

    private String prizeName;

    private Byte prizeType;

    private Integer prizeValue;

    private Integer weight;

    private Integer stockRemaining;

    private Byte isJackpot;

    private Byte isMysteryBundle;

    private String imagePath;

    private Byte catalogStatus;
}
