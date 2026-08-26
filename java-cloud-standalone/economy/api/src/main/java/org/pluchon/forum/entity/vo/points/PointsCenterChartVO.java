package org.pluchon.forum.entity.vo.points;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

// 萌币流水图表聚合
@Data
@Schema(description = "萌币流水图表聚合")
public class PointsCenterChartVO {

    private Integer incomeTotal;

    private Integer expenseTotal;

    private List<PointsSourceSummaryVO> incomeSources;

    private List<PointsSourceSummaryVO> expenseSources;
}
