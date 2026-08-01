package org.example.forumdemo.entity.vo.growth;

import lombok.Data;

// 成长挑战提交结果
@Data
public class GrowthSubmitResultVO {
    private Boolean passed;
    private Integer score;
    private Boolean formalUser;
    private Integer experience;
    private String message;
}
