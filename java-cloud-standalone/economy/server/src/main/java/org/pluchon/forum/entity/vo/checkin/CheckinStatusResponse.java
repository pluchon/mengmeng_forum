package org.pluchon.forum.entity.vo.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

// 签到状态查询响应：用于进入签到页时展示日历高亮、是否已签、下一档奖励等
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "签到状态快照")
public class CheckinStatusResponse {

    @Schema(description = "当前连续签到天数, 未签到过为 0")
    private Integer streakDays;

    @Schema(description = "累计签到天数")
    private Integer totalDays;

    @Schema(description = "累计签到积分")
    private Integer totalPoints;

    @Schema(description = "最后一次签到日期, 从未签到为 null")
    private Date lastCheckin;

    @Schema(description = "今日是否已签到")
    private Boolean todaySigned;

    @Schema(description = "下一档连签奖励门槛, 已无更高档为 null")
    private Integer nextThreshold;

    @Schema(description = "下一档连签奖励积分, 已无更高档为 null")
    private Integer nextThresholdBonus;

    @Schema(description = "距离下一档奖励还差的天数, 已无更高档为 null")
    private Integer nextThresholdLeft;

    @Schema(description = "持有补签卡数量")
    private Integer makeupCardCount;

    @Schema(description = "完整连签阶梯")
    private List<CheckinStreakRewardItemVO> streakRewards;
}
