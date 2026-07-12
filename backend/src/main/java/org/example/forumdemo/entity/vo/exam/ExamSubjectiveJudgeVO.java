package org.example.forumdemo.entity.vo.exam;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 主观题评分结果
@Data
public class ExamSubjectiveJudgeVO {

    // 匹配分数
    private Integer score;

    // 是否通过
    private Boolean passed;

    // 评分说明
    private String comment;

    // 命中要点
    private List<String> matchedPoints = new ArrayList<>();

    // 缺失要点
    private List<String> missedPoints = new ArrayList<>();

    // 是否使用本地兜底评分
    private Boolean fallback;
}
