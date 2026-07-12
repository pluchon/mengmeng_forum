package org.example.forumdemo.entity.vo.exam;

import lombok.Data;

// 题目选项
@Data
public class ExamQuestionOptionVO {

    // 选项标识
    private String label;

    // 选项内容
    private String text;
}
