package org.pluchon.forum.entity.vo.growth;

import lombok.Data;

// 成长挑战概要
@Data
public class GrowthChallengeVO {
    private String challengeCode;
    private String title;
    private String description;
    private String status;
    private Integer questionCount;
    private Integer passingScore;
    private Integer experienceReward;
}
