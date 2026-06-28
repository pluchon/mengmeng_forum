package org.example.forumdemo.entity.vo.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "签到日趋势明细")
public class CheckinDayTrendVO {

    @Schema(description = "签到日期")
    private Date checkinDate;

    @Schema(description = "当日获得萌币（基础+奖励）")
    private Integer points;
}
