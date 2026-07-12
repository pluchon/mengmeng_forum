package org.example.forumdemo.entity.vo.exam;

import lombok.Data;

// 考试题库用户答题进度
@Data
public class ExamQuestionProgressVO {

    // 题目 ID
    private String questionId;

    // 用户答案
    private String answerText;

    // 是否已作答
    private Boolean answered;

    // 是否答对
    private Boolean correct;

    // 是否错题
    private Boolean wrong;

    // 是否重点记忆
    private Boolean focus;

    // 主观题评分
    private Integer judgeScore;
}
