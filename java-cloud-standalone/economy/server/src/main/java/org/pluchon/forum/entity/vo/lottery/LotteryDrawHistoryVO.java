package org.pluchon.forum.entity.vo.lottery;

import lombok.Data;

import java.util.Date;

// 用户单次开奖历史（十连拆成多条）
@Data
public class LotteryDrawHistoryVO {

    private Long drawRecordId;

    private Long drawRequestId;

    private String prizeSummary;

    // 奖品稀有度 prize_type：0谢谢 1大奖 2小奖 3安慰 4积分 5VIP；头奖按大奖展示
    private Byte prizeType;

    private String rewardSummary;

    private String costMethod;

    private Date createTime;
}
