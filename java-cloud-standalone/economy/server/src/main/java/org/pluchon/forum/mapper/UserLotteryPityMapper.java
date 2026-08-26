package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.UserLotteryPity;

// 抽奖保底 Mapper economy
@Mapper
public interface UserLotteryPityMapper extends BaseMapper<UserLotteryPity> {

    @Select("SELECT * FROM user_lottery_pity WHERE user_id = #{userId} AND delete_state = 0 FOR UPDATE")
    UserLotteryPity selectByUserIdForUpdate(@Param("userId") Long userId);

    @Select("SELECT * FROM user_lottery_pity WHERE user_id = #{userId} AND delete_state = 0 LIMIT 1")
    UserLotteryPity selectByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO user_lottery_pity (user_id, pity_draws, delete_state) VALUES (#{userId}, #{pityDraws}, 0)")
    int insertPity(@Param("userId") Long userId, @Param("pityDraws") int pityDraws);

    @Update("UPDATE user_lottery_pity SET pity_draws = 0 WHERE user_id = #{userId} AND delete_state = 0")
    int resetPityDraws(@Param("userId") Long userId);

    @Update("UPDATE user_lottery_pity SET pity_draws = #{pityDraws} WHERE user_id = #{userId} AND delete_state = 0")
    int updatePityDraws(@Param("userId") Long userId, @Param("pityDraws") int pityDraws);
}
