package org.example.forumdemo.entity.dto.admin;

import lombok.Data;

@Data
public class AdminLotteryPrizeLineSaveDTO {

    /** 已有活动奖品行时传入，用于更新 */
    private Long activityPrizeId;

    /** 引用奖品库时传入 lottery_prize.id；与「手写新建」二选一 */
    private Long prizeId;

    private String name;

    private Byte prizeType;

    private Integer prizeValue;

    private Integer weight;

    private Integer stockRemaining;

    private Byte isJackpot;

    /** 写入 lottery_activity_prize.image_path，可空 */
    private String imagePath;
}
