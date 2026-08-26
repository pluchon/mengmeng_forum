package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.pluchon.forum.entity.db.ForumMascotMemory;

@Mapper
public interface ForumMascotMemoryMapper extends BaseMapper<ForumMascotMemory> {

    @Select("SELECT * FROM forum_mascot_memory WHERE user_id = #{userId} AND delete_state = 0 LIMIT 1")
    ForumMascotMemory selectByUserId(Long userId);
}
