package org.example.forumdemo.entity.vo.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 月度签到规则响应, 用于前端日历展示
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "月度签到规则")
public class CheckinRuleMonthResponse {

    @Schema(description = "月份 (1-12)")
    private Integer month;

    @Schema(description = "本月每日规则, 按 dayNumber 升序")
    private List<CheckinRuleDayResponse> days;
}
