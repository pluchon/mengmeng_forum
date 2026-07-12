package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.forumdemo.entity.db.ExamQuestionUserProgress;

// 考试题库用户答题进度 Mapper
@Mapper
public interface ExamQuestionUserProgressMapper extends BaseMapper<ExamQuestionUserProgress> {
}
