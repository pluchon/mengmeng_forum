package org.example.forumdemo.entity.vo.exam;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 临时题库解析结果
@Data
public class ExamQuestionBankVO {

    // 题库 ID
    private Long bankId;

    // 考试科目
    private String subject;

    // 来源文件名
    private String sourceName;

    // 总题数
    private Integer totalCount;

    // 选择题数量
    private Integer choiceCount;

    // 判断题数量
    private Integer judgementCount;

    // 大题数量
    private Integer subjectiveCount;

    // 解析警告
    private List<String> warnings = new ArrayList<>();

    // 题目列表
    private List<ExamQuestionVO> questions = new ArrayList<>();
}
