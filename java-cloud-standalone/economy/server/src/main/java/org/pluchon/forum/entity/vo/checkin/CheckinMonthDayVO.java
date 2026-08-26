package org.pluchon.forum.entity.vo.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 月历单日
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "签到月历单日")
public class CheckinMonthDayVO {

    @Schema(description = "日期 yyyy-MM-dd")
    private String date;

    @Schema(description = "当月第几天")
    private Integer dayNumber;

    @Schema(description = "当日基础积分")
    private Integer points;

    @Schema(description = "是否已签到")
    private Boolean signed;

    @Schema(description = "是否补签")
    private Boolean makeup;

    @Schema(description = "是否惊喜日")
    private Boolean surpriseDay;

    @Schema(description = "是否今天")
    private Boolean today;
}
