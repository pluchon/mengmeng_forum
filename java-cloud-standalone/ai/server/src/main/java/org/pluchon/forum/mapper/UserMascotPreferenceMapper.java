package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.UserMascotPreference;

// 看板娘偏好 Mapper ai
@Mapper
public interface UserMascotPreferenceMapper extends BaseMapper<UserMascotPreference> {

    @Select("SELECT * FROM user_mascot_preference WHERE user_id = #{userId} AND delete_state = 0 LIMIT 1")
    UserMascotPreference selectByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO user_mascot_preference (user_id, mascot_model_id, delete_state) "
            + "VALUES (#{userId}, #{mascotModelId}, 0)")
    void insertPreference(@Param("userId") Long userId, @Param("mascotModelId") Long mascotModelId);

    @Update("UPDATE user_mascot_preference SET mascot_model_id = #{mascotModelId} "
            + "WHERE user_id = #{userId} AND delete_state = 0")
    void updatePreference(@Param("userId") Long userId, @Param("mascotModelId") Long mascotModelId);
}
