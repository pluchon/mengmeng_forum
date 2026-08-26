package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.UserProfileChangeRequest;

@Mapper
public interface UserProfileChangeRequestMapper extends BaseMapper<UserProfileChangeRequest> {
}
