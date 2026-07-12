package org.example.forumdemo.entity.dto.exam;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.forumdemo.entity.vo.exam.ExamQuestionOptionVO;

import java.util.ArrayList;
import java.util.List;

// 考试题库题目编辑请求
@Data
public class ExamQuestionEditRequest {

    // 题库 ID
    @NotNull(message = "题库不能为空")
    private Long bankId;

    // 题目 ID
    @NotNull(message = "题目不能为空")
    private Long questionId;

    // 题干
    @NotBlank(message = "题干不能为空")
    private String stem;

    // 选项
    private List<ExamQuestionOptionVO> options = new ArrayList<>();

    // 标准答案
    private String answer;

    // 解析
    private String explanation;
}
