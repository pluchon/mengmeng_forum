package org.example.forumdemo.entity.vo.growth;

import lombok.Data;

import java.util.List;

// 成长中心总览
@Data
public class GrowthOverviewVO {
    private Boolean formalUser;
    private Integer experience;
    private Integer growthLevel;
    private Integer nextLevelExperience;
    private List<GrowthChallengeVO> challenges;
}
