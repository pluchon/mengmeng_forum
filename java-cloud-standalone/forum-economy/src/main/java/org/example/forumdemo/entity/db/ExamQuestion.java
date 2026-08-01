package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 考试题库题目表
@Data
@TableName("exam_question")
public class ExamQuestion {

    // 题目 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 题库 ID
    private Long bankId;

    // 题目顺序
    private Integer questionOrder;

    // 原文题号
    private String sourceNo;

    // 原文章节或分组
    private String sectionName;

    // 题目类型
    private String questionType;

    // 题干
    private String stem;

    // 选项 JSON
    private String optionsJson;

    // 标准答案
    private String standardAnswer;

    // 解析
    private String explanation;

    // 答案是否从用户答案推断
    private Byte answerInferredFromUser;

    // 是否需要人工复核选项
    private Byte needsOptionReview;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;

    // 是否删除: 0否 1是
    private Byte deleteState;
}
