package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

@Data
public class AdminLotteryDrawUserRowVO {

    private Long userId;

    private String nickname;

    private String avatarUrl;

    private Byte vipTier;

    private String vipExpireAt;

    /** 该用户在本活动下的抽奖次数 */
    private Integer drawCount;

    /** 最近一次抽奖时间 */
    private String lastDrawTime;
}
