package org.example.forumdemo.entity.vo.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 签到萌币按月趋势 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "签到积分月度趋势")
public class CheckinMonthTrendResponse {

    @Schema(description = "年份")
    private Integer year;

    @Schema(description = "月份 1-12")
    private Integer month;

    @Schema(description = "当月签到总天数")
    private Integer totalDays;

    @Schema(description = "当月获得萌币合计（基础+连续奖励）")
    private Integer totalPoints;

    @Schema(description = "按日明细")
    private List<CheckinDayTrendVO> days;
}
