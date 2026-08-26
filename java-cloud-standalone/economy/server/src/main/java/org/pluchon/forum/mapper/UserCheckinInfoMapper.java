package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.UserCheckinInfo;

// 用户签到状态 Mapper
@Mapper
public interface UserCheckinInfoMapper extends BaseMapper<UserCheckinInfo> {
}
