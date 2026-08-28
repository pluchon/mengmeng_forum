package org.pluchon.forum.entity.vo.creator;

import lombok.Data;

import java.util.List;

// 创作中心统计面板
@Data
public class CreatorDashboardVO {

    private Integer totalLikeCount;
    private Integer totalWorkCount;
    private Integer monthNewReadCount;
    private Integer monthNewLikeCount;
    private Integer monthNewWorkCount;
    private String weekStart;
    private List<CreatorDailyTrendVO> trendDays;
}
