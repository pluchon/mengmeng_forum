package org.example.forumdemo.entity.vo.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单日签到规则项：当月第几天可获得多少积分
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单日签到积分规则")
public class CheckinRuleDayResponse {

    @Schema(description = "当月第几天 (1-31)")
    private Integer dayNumber;

    @Schema(description = "签到可得积分")
    private Integer points;
}
