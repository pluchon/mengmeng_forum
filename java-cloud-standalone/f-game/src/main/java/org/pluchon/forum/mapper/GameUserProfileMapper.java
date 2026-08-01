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
    int updatePlayStatus(@Param("userId") Long userId,
                         @Param("gameCode") String gameCode,
                         @Param("status") String status,
                         @Param("roomId") String roomId);

    @Update("UPDATE game_user_profile SET score = score + #{scoreDelta}, "
            + "total_count = total_count + 1, win_count = win_count + 1, "
            + "current_status = 'IDLE', current_room_id = NULL "
            + "WHERE user_id = #{userId} AND game_code = #{gameCode} AND delete_state = 0")
    int applyWin(@Param("userId") Long userId,
                 @Param("gameCode") String gameCode,
                 @Param("scoreDelta") int scoreDelta);

    @Update("UPDATE game_user_profile SET score = GREATEST(score - #{scoreDelta}, 0), "
            + "total_count = total_count + 1, lose_count = lose_count + 1, "
            + "current_status = 'IDLE', current_room_id = NULL "
            + "WHERE user_id = #{userId} AND game_code = #{gameCode} AND delete_state = 0")
    int applyLose(@Param("userId") Long userId,
                  @Param("gameCode") String gameCode,
                  @Param("scoreDelta") int scoreDelta);

    @Update("UPDATE game_user_profile SET total_count = total_count + 1, draw_count = draw_count + 1, "
            + "current_status = 'IDLE', current_room_id = NULL "
            + "WHERE user_id = #{userId} AND game_code = #{gameCode} AND delete_state = 0")
    int applyDraw(@Param("userId") Long userId,
                  @Param("gameCode") String gameCode);

    @Update("UPDATE game_user_profile SET score = GREATEST(score, #{runScore}), "
            + "total_count = total_count + 1 "
            + "WHERE user_id = #{userId} AND game_code = #{gameCode} AND delete_state = 0")
    int applyTetrisFinish(@Param("userId") Long userId, @Param("gameCode") String gameCode, @Param("runScore") int runScore);
}
