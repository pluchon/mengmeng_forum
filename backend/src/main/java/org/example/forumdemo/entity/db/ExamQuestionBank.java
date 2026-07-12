package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 考试题库主表
@Data
@TableName("exam_question_bank")
public class ExamQuestionBank {

    // 题库 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 创建用户 ID
    private Long userId;

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

    // 主观题数量
    private Integer subjectiveCount;

    // 解析警告 JSON
    private String warningsJson;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;

    // 是否删除: 0否 1是
    private Byte deleteState;
}
