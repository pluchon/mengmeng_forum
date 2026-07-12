package org.example.forumdemo.common.enums;

import lombok.Getter;

// 考试题库题目类型
@Getter
public enum ExamQuestionType {
    SINGLE("single", "单选题"),
    MULTIPLE("multiple", "多选题"),
    JUDGEMENT("judgement", "判断题"),
    SUBJECTIVE("subjective", "大题");

    private final String code;
    private final String label;

    ExamQuestionType(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
