package org.pluchon.forum.entity.vo.growth;

import lombok.Data;

// 挑战题目（不返回标准答案）
@Data
public class GrowthQuestionVO {
    private Long id;
    private Integer questionOrder;
    private String questionType;
    private String stem;
    private String optionsJson;
}
