package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.forumdemo.entity.db.LotteryDrawHourlyStat;
import org.example.forumdemo.entity.vo.lottery.LotteryHourStatRow;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface LotteryDrawHourlyStatMapper extends BaseMapper<LotteryDrawHourlyStat> {

    @Insert("INSERT INTO lottery_draw_hourly_stat (activity_id, stat_hour, draw_count) VALUES (#{activityId}, #{statHour}, 1) "
            + "ON DUPLICATE KEY UPDATE draw_count = draw_count + 1, update_time = CURRENT_TIMESTAMP")
    int incrementCount(@Param("activityId") Long activityId, @Param("statHour") Timestamp statHour);

    @Select("SELECT stat_hour AS statHour, draw_count AS drawCount FROM lottery_draw_hourly_stat "
            + "WHERE activity_id = #{activityId} AND stat_hour >= #{fromHour} ORDER BY stat_hour ASC")
    List<LotteryHourStatRow> selectSince(@Param("activityId") Long activityId, @Param("fromHour") Timestamp fromHour);
}
