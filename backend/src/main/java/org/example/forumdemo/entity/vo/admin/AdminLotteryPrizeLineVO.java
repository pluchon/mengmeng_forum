package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

@Data
public class AdminLotteryPrizeLineVO {

    private Long activityPrizeId;

    private Long prizeId;

    private String name;

    private Byte prizeType;

    private Integer prizeValue;

    private Integer weight;

    private Integer stockRemaining;

    private Byte isJackpot;

    private String imagePath;

    private Byte isMysteryBundle;

    private Byte catalogStatus;
}
