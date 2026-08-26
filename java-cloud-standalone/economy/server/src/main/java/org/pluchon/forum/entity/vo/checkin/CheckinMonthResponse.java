package org.pluchon.forum.entity.vo.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 月度签到摘要
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "签到月度摘要")
public class CheckinMonthResponse {

    @Schema(description = "年")
    private Integer year;

    @Schema(description = "月 1-12")
    private Integer month;

    @Schema(description = "本月已签天数")
    private Integer monthSignedDays;

    @Schema(description = "每日状态")
    private List<CheckinMonthDayVO> days;

    @Schema(description = "按自然周聚合的签到天数")
    private List<CheckinWeekStatVO> weeklyStats;
}
