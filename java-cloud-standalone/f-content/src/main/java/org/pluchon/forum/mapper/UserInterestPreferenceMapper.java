package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.UserInterestPreference;

// 用户兴趣偏好数据访问
@Mapper
public interface UserInterestPreferenceMapper extends BaseMapper<UserInterestPreference> {
}
