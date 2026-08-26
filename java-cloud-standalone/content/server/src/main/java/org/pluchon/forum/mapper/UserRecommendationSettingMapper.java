package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.UserRecommendationSetting;

// 用户推荐设置数据访问
@Mapper
public interface UserRecommendationSettingMapper extends BaseMapper<UserRecommendationSetting> {
}
