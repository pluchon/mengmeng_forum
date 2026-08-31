package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.pluchon.forum.entity.db.PointsLog;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface PointsLogMapper extends BaseMapper<PointsLog> {

    // 按用户 + 日期范围按自然日聚合积分变动，服务器时区统一 Asia/Taipei
    @Select("""
            SELECT DATE(create_time) AS day,
                   SUM(CASE WHEN delta > 0 THEN delta ELSE 0 END) AS in_total,
                   SUM(CASE WHEN delta < 0 THEN -delta ELSE 0 END) AS out_total,
                   SUM(delta) AS net
              FROM points_log
             WHERE user_id = #{userId}
               AND delete_state = 0
               AND create_time >= #{from}
               AND create_time <  #{to}
             GROUP BY DATE(create_time)
             ORDER BY day ASC
            """)
    List<Map<String, Object>> selectDailyAggregation(@Param("userId") Long userId,
                                                     @Param("from") Date from,
                                                     @Param("to") Date to);

    // 历史正向萌币累计，用于里程碑解锁。
    // 排除里程碑奖励自身，否则领了 M1000 的 +50 会把人往 M2000 推，累计变成自我喂养
    @Select("""
            SELECT COALESCE(SUM(delta), 0)
              FROM points_log
             WHERE user_id = #{userId}
               AND delete_state = 0
               AND delta > 0
               AND source_type <> #{excludeSourceType}
            """)
    Integer sumPositiveExcluding(@Param("userId") Long userId,
                                 @Param("excludeSourceType") Byte excludeSourceType);

    // 指定周期的收入或消耗来源排行
    @Select("""
            SELECT source_type, SUM(CASE WHEN delta > 0 THEN delta ELSE -delta END) AS amount
              FROM points_log
             WHERE user_id = #{userId}
               AND delete_state = 0
               AND create_time >= #{from}
               AND create_time < #{to}
               AND ((#{income} = 1 AND delta > 0) OR (#{income} = 0 AND delta < 0))
             GROUP BY source_type
             ORDER BY amount DESC, source_type ASC
             LIMIT 3
            """)
    List<Map<String, Object>> selectTopSources(@Param("userId") Long userId,
                                               @Param("from") Date from,
                                               @Param("to") Date to,
                                               @Param("income") int income);

    // 按流水筛选条件聚合收入、支出来源
    @Select("""
            <script>
            SELECT source_type,
                   SUM(CASE WHEN delta &gt; 0 THEN delta ELSE 0 END) AS in_total,
                   SUM(CASE WHEN delta &lt; 0 THEN -delta ELSE 0 END) AS out_total
              FROM points_log
             WHERE user_id = #{userId}
               AND delete_state = 0
               AND create_time &gt;= #{from}
               AND create_time &lt; #{to}
               <if test="sourceType != null">AND source_type = #{sourceType}</if>
               <if test="direction == 'INCOME'">AND delta &gt; 0</if>
               <if test="direction == 'EXPENSE'">AND delta &lt; 0</if>
             GROUP BY source_type
             ORDER BY (in_total + out_total) DESC, source_type ASC
            </script>
            """)
    List<Map<String, Object>> selectSourceAggregation(@Param("userId") Long userId,
                                                       @Param("from") Date from,
                                                       @Param("to") Date to,
                                                       @Param("direction") String direction,
                                                       @Param("sourceType") Byte sourceType);
}
