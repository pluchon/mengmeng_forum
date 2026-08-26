package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.pluchon.forum.entity.db.UserLotteryTaskClaim;

import java.time.LocalDate;

@Mapper
public interface UserLotteryTaskClaimMapper extends BaseMapper<UserLotteryTaskClaim> {

    @Select("SELECT * FROM user_lottery_task_claim "
            + "WHERE user_id = #{userId} AND activity_id = #{activityId} "
            + "AND task_code = #{taskCode} AND claim_date = #{claimDate} AND delete_state = 0 "
            + "LIMIT 1")
    UserLotteryTaskClaim selectOneClaim(@Param("userId") Long userId,
                                        @Param("activityId") Long activityId,
                                        @Param("taskCode") String taskCode,
                                        @Param("claimDate") LocalDate claimDate);
}
