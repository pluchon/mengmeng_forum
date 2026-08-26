package org.pluchon.forum.entity.vo.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 连签阶梯单项
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "连签阶梯展示项")
public class CheckinStreakRewardItemVO {

    @Schema(description = "连续天数门槛")
    private Integer streakDays;

    @Schema(description = "奖励类型")
    private String rewardType;

    @Schema(description = "主文案")
    private String title;

    @Schema(description = "副文案")
    private String subtitle;

    @Schema(description = "是否已达成")
    private Boolean achieved;

    @Schema(description = "距离达成还差天数, 已达成为 0")
    private Integer daysLeft;
}
