package org.pluchon.forum.entity.vo.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 签到执行接口响应：本次得分 + 签后最新状态，前端无需二次查询
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "签到执行结果")
public class CheckinResultResponse {

    @Schema(description = "本次签到基础积分")
    private Integer todayPoints;

    @Schema(description = "本次连续签到额外奖励, 未触发为 0")
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
}
