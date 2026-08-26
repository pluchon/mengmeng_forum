package org.pluchon.forum.entity.vo.points;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

// 萌币中心单周趋势
@Data
@Schema(description = "萌币中心单周趋势")
public class PointsCenterTrendVO {

    private String trendStartDate;

    private String trendEndDate;

    private Boolean hasPreviousWeek;

    private Boolean hasNextWeek;

    private Boolean trendWeekComplete;

    private List<PointsDailyVO> dailyTrend;
}
