package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.forumdemo.entity.db.ExamQuestion;

// 考试题目 Mapper
@Mapper
public interface ExamQuestionMapper extends BaseMapper<ExamQuestion> {
}
