package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.pluchon.forum.entity.db.UserLotteryCollectClaim;

import java.util.List;

@Mapper
public interface UserLotteryCollectClaimMapper extends BaseMapper<UserLotteryCollectClaim> {

    @Select("SELECT threshold_count FROM user_lottery_collect_claim "
            + "WHERE user_id = #{userId} AND activity_id = #{activityId} AND delete_state = 0 "
            + "ORDER BY threshold_count ASC")
    List<Integer> selectClaimedThresholds(@Param("userId") Long userId, @Param("activityId") Long activityId);
}
