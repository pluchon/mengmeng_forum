package org.pluchon.forum.entity.vo.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 月内周统计
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "签到周统计")
public class CheckinWeekStatVO {

    @Schema(description = "第几周, 从 1 开始")
    private Integer weekIndex;

    @Schema(description = "该周已签天数")
    private Integer days;
}
