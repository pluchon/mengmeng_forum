package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.User;

// 作者代码水平一般，难免难看，请见谅
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
