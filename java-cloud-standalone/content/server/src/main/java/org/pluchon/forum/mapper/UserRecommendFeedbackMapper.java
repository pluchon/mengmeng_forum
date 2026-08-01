package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.UserRecommendFeedback;

// 用户推荐反馈数据访问
@Mapper
public interface UserRecommendFeedbackMapper extends BaseMapper<UserRecommendFeedback> {
}
