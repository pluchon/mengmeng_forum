package org.pluchon.forum.entity.vo.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

// 签到执行接口响应：本次得分 + 签后最新状态，前端无需二次查询
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "签到执行结果")
public class CheckinResultResponse {

    @Schema(description = "本次签到基础积分")
    private Integer todayPoints;

    @Schema(description = "本次连续签到额外奖励积分, 未触发为 0")
    private Integer bonusPoints;

    @Schema(description = "本次连签奖励描述, 未触发为 null")
    private String bonusDescription;

    @Schema(description = "签到完成后的连续签到天数")
    private Integer streakDays;

    @Schema(description = "签到完成后的累计签到天数")
    private Integer totalDays;

    @Schema(description = "签到完成后的累计签到积分")
    private Integer totalPoints;

    @Schema(description = "本次签到日期")
    private Date lastCheckin;

    @Schema(description = "持有补签卡数量")
    private Integer makeupCardCount;

    @Schema(description = "惊喜奖励类型")
    private String surpriseType;

    @Schema(description = "惊喜奖励数值")
    private Integer surpriseValue;

    @Schema(description = "惊喜奖励文案")
    private String surpriseLabel;

    @Schema(description = "完整连签阶梯")
    private List<CheckinStreakRewardItemVO> streakRewards;
}
