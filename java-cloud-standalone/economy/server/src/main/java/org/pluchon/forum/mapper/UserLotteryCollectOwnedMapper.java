package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.pluchon.forum.entity.db.UserLotteryCollectOwned;

import java.util.List;

@Mapper
public interface UserLotteryCollectOwnedMapper extends BaseMapper<UserLotteryCollectOwned> {

    @Select("SELECT icon_id FROM user_lottery_collect_owned "
            + "WHERE user_id = #{userId} AND activity_id = #{activityId} AND delete_state = 0 "
            + "ORDER BY icon_id ASC")
    List<Integer> selectOwnedIconIds(@Param("userId") Long userId, @Param("activityId") Long activityId);
}
