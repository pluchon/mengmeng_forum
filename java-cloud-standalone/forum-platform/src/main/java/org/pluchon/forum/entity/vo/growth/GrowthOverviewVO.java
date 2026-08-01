package org.pluchon.forum.entity.vo.growth;

import lombok.Data;

// 成长中心总览
@Data
public class GrowthOverviewVO {
    private Boolean formalUser;
    private Integer experience;
    private Integer growthLevel;
    private Integer currentLevelExperience;
    private Integer nextLevelExperience;
}
