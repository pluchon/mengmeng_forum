package org.pluchon.forum.entity.vo.creator;

import lombok.Data;

import java.util.List;

// 创作中心 AI 数据小结
@Data
public class CreatorInsightVO {

    private String period;

    private String periodLabel;

    private String startDate;

    private String endDate;

    private String headline;

    private String overview;

    private String highlight;

    private List<String> highlights;

    private String generatedAt;

    private Boolean cached;
}
