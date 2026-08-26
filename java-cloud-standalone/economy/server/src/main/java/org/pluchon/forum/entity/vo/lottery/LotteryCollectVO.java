package org.pluchon.forum.entity.vo.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "幸运收集册进度")
public class LotteryCollectVO {

    private Integer totalIcons;

    private Integer ownedCount;

    private List<Integer> ownedIconIds = new ArrayList<>();

    private List<Integer> claimedThresholds = new ArrayList<>();

    private List<LotteryCollectMilestoneVO> milestones = new ArrayList<>();
}
