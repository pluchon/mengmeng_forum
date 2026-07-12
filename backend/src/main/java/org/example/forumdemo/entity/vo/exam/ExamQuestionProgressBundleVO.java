package org.example.forumdemo.entity.vo.exam;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 考试题库用户答题进度集合
@Data
public class ExamQuestionProgressBundleVO {

    // 题库 ID
    private Long bankId;

    // 答题进度
    private List<ExamQuestionProgressVO> records = new ArrayList<>();
}
