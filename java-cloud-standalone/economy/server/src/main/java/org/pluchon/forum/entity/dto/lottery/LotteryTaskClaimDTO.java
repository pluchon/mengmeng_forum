package org.pluchon.forum.entity.dto.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "领取卡池任务奖励")
public class LotteryTaskClaimDTO {

    @Schema(description = "活动/卡池 ID")
    private Long activityId;

    @Schema(description = "任务编码 COMMENT_1 / LIKE_3 / CHECKIN_TODAY")
    private String taskCode;
}
