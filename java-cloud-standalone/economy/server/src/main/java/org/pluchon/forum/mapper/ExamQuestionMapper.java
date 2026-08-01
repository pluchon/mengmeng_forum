package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.ExamQuestion;

// 考试题目 Mapper
@Mapper
public interface ExamQuestionMapper extends BaseMapper<ExamQuestion> {
}
