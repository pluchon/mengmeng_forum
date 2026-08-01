package org.example.forumdemo.entity.vo.growth;

import lombok.Data;

import java.util.List;

// 已开始的成长挑战
@Data
public class GrowthChallengeDetailVO {
    private Long attemptId;
    private String challengeCode;
    private String title;
    private Integer passingScore;
    private List<GrowthQuestionVO> questions;
}
