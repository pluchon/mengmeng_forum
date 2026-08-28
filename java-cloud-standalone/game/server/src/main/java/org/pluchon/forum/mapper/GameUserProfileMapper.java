package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.GameUserProfile;

@Mapper
public interface GameUserProfileMapper extends BaseMapper<GameUserProfile> {

    @Update("UPDATE game_user_profile SET current_status = #{status}, current_room_id = #{roomId} "
            + "WHERE user_id = #{userId} AND game_code = #{gameCode} AND delete_state = 0")
    void updatePlayStatus(@Param("userId") Long userId,
                         @Param("gameCode") String gameCode,
                         @Param("status") String status,
                         @Param("roomId") String roomId);

    @Update("UPDATE game_user_profile SET score = GREATEST(score, #{runScore}), "
            + "total_count = total_count + 1 "
            + "WHERE user_id = #{userId} AND game_code = #{gameCode} AND delete_state = 0")
    int applyTetrisFinish(@Param("userId") Long userId, @Param("gameCode") String gameCode, @Param("runScore") int runScore);
}
