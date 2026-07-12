package org.example.forumdemo.entity.dto.exam;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// 主观题评分请求
@Data
public class ExamSubjectiveJudgeRequest {

    // 考试科目
    @NotBlank(message = "考试科目不能为空")
    private String subject;

    // 题干
    @NotBlank(message = "题干不能为空")
    private String question;

    // 标准答案
    @NotBlank(message = "标准答案不能为空")
    private String standardAnswer;

    // 用户答案
    @NotBlank(message = "用户答案不能为空")
    private String userAnswer;
}
