package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.forumdemo.entity.db.UserCheckinInfo;

/**
 * 用户签到状态 Mapper
 */
@Mapper
public interface UserCheckinInfoMapper extends BaseMapper<UserCheckinInfo> {
}
