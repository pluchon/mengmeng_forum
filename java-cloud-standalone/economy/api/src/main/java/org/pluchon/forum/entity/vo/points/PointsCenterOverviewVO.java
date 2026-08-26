package org.pluchon.forum.entity.vo.points;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

// 萌币中心顶部统计、里程碑与来源回顾
@Data
@Schema(description = "萌币中心概览")
public class PointsCenterOverviewVO {

    @Schema(description = "当前萌币余额")
    private Integer balance;

    @Schema(description = "本月获得")
    private Integer monthIncome;

    @Schema(description = "本月消耗")
    private Integer monthExpense;

    @Schema(description = "趋势起始日期", example = "2026-08-01")
    private String trendStartDate;

    @Schema(description = "趋势结束日期", example = "2026-08-10")
    private String trendEndDate;

    @Schema(description = "是否存在上一周")
    private Boolean hasPreviousWeek;

    @Schema(description = "是否存在下一周")
    private Boolean hasNextWeek;

    @Schema(description = "当前周区间是否已完整结束")
    private Boolean trendWeekComplete;

    @Schema(description = "本月按日收支")
    private List<PointsDailyVO> dailyTrend;

    @Schema(description = "历史累计获得萌币")
    private Integer cumulativeIncome;

    @Schema(description = "里程碑")
    private List<PointsMilestoneVO> milestones;

    @Schema(description = "本月主要收入来源")
    private List<PointsSourceSummaryVO> incomeSources;

    @Schema(description = "本月主要消耗来源")
    private List<PointsSourceSummaryVO> expenseSources;
}
