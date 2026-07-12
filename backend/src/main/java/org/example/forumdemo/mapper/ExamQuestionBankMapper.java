package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.forumdemo.entity.db.ExamQuestionBank;

// 考试题库 Mapper
@Mapper
public interface ExamQuestionBankMapper extends BaseMapper<ExamQuestionBank> {
}
