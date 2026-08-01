package org.pluchon.forum.entity.vo.points;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 积分按"自然日"聚合, 给前端 ECharts 折线/柱状图直接喂数据.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "积分按日聚合条目")
public class PointsDailyVO {

    @Schema(description = "自然日, 格式 yyyy-MM-dd")
    private String day;

    @Schema(description = "当日入账总和")
    private Integer inTotal;

    @Schema(description = "当日消费总和(绝对值)")
    private Integer outTotal;

    @Schema(description = "当日净变动 = inTotal - outTotal")
    private Integer net;
}
