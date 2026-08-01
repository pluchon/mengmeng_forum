package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.pluchon.forum.entity.db.LotteryDrawHourlyStat;

import java.sql.Timestamp;

@Mapper
public interface LotteryDrawHourlyStatMapper extends BaseMapper<LotteryDrawHourlyStat> {

    @Insert("INSERT INTO lottery_draw_hourly_stat (activity_id, stat_hour, draw_count) VALUES (#{activityId}, #{statHour}, 1) "
            + "ON DUPLICATE KEY UPDATE draw_count = draw_count + 1, update_time = CURRENT_TIMESTAMP")
    int incrementCount(@Param("activityId") Long activityId, @Param("statHour") Timestamp statHour);
}
