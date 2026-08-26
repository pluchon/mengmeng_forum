package org.pluchon.forum.entity.dto.lottery;

import lombok.Data;

@Data
public class LotteryCollectClaimDTO {

    private Long activityId;

    private Integer thresholdCount;
}
