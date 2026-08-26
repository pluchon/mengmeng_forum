package org.pluchon.forum.entity.vo.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "幸运收集册里程节点")
public class LotteryCollectMilestoneVO {

    private Integer thresholdCount;

    private String rewardType;

    private Integer rewardValue;

    private Integer altRewardValue;

    private String label;

    private Boolean claimed;

    private Boolean reachable;
}
