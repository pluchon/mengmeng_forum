package org.example.forumdemo.entity.vo.exam;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 题库题目
@Data
public class ExamQuestionVO {

    // 页面内唯一题目 ID
    private String id;

    // 原文题号
    private String sourceNo;

    // 原文章节或分组
    private String section;

    // 题目类型
    private String type;

    // 题干
    private String stem;

    // 选项
    private List<ExamQuestionOptionVO> options = new ArrayList<>();

    // 标准答案
    private String answer;

    // 解析
    private String explanation;

    // 答案是否从“你的答案”推断
    private Boolean answerInferredFromUser;

    // 是否需要人工补全选项
    private Boolean needsOptionReview;
}
