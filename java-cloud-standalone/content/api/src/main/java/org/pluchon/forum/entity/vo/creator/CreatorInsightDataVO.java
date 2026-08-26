package org.pluchon.forum.entity.vo.creator;

import lombok.Data;

import java.util.List;

// 创作中心趋势与可复用小结
@Data
public class CreatorInsightDataVO {

    private String period;

    private String periodLabel;

    private String startDate;

    private String endDate;

    private List<CreatorTrendPointVO> trendPoints;

    private CreatorInsightVO insight;
}
