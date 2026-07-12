package org.example.forumdemo.entity.dto.exam;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 考试题库用户答题进度请求
@Data
public class ExamQuestionProgressRequest {

    // 题库 ID
    @NotNull(message = "题库不能为空")
    private Long bankId;

    // 题目 ID
    @NotNull(message = "题目不能为空")
    private Long questionId;

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
