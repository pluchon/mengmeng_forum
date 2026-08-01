package org.pluchon.forum.entity.vo.lottery;

import lombok.Data;

import java.util.Date;

// 用户抽奖记录分页响应
@Data
public class LotteryDrawRecordVO {

    // 抽奖记录 ID
    private Long recordId;

    // 奖品名称
    private String prizeName;

    // 奖励明细快照
    private String rewardDetail;

    // 1：十连批次中的一抽；0：单抽
    private Integer multiDraw;

    // 抽奖时间
    private Date createTime;
}
