package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.pluchon.forum.entity.db.LotteryPoolTask;

import java.util.List;

@Mapper
public interface LotteryPoolTaskMapper extends BaseMapper<LotteryPoolTask> {

    @Select("SELECT * FROM lottery_pool_task "
            + "WHERE activity_id = #{activityId} AND enabled = 1 AND delete_state = 0 "
            + "ORDER BY sort_order ASC, id ASC")
    List<LotteryPoolTask> selectEnabledByActivityId(@Param("activityId") Long activityId);
}
