package org.pluchon.forum.entity.dto.points;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

// 萌币流水筛选条件
@Data
@Schema(description = "萌币流水筛选条件")
public class PointsLogQueryDTO {

    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页数量", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "收支类型：ALL / INCOME / EXPENSE", example = "ALL")
    private String direction = "ALL";

    @Schema(description = "来源类型，不传表示全部")
    private Byte sourceType;

    @Schema(description = "时间范围：LAST_7_DAYS / LAST_30_DAYS / THIS_MONTH", example = "LAST_30_DAYS")
    private String timeRange = "LAST_30_DAYS";
}
