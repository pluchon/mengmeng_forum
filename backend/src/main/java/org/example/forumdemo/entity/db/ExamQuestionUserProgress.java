package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 考试题库用户答题进度表
@Data
@TableName("exam_question_user_progress")
public class ExamQuestionUserProgress {

    // 进度 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户 ID
    private Long userId;

    // 题库 ID
    private Long bankId;

    // 题目 ID
    private Long questionId;

    // 用户答案
    private String answerText;

    // 是否已作答
    private Byte answered;

    // 是否答对
    private Byte correct;

    // 是否错题
    private Byte wrong;

    // 是否重点记忆
    private Byte focus;

    // 主观题评分
    private Integer judgeScore;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;

    // 是否删除: 0否 1是
    private Byte deleteState;
}
