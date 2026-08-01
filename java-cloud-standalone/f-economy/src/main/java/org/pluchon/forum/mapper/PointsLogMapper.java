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

    /**
     * 按用户 + 日期范围按"自然日"聚合积分变动, 供前端 ECharts 渲染收益曲线.
     * 仅统计未删除流水. 返回字段:
     *   day        - DATE 形式的自然日 (服务器时区, 业务侧已统一 Asia/Shanghai)
     *   in_total   - 当日入账总和 (delta > 0)
     *   out_total  - 当日消费总和的绝对值 (delta < 0)
     *   net        - 当日净变动 (in_total - out_total)
     */
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

    /**
     * 按正向来源统计入账总额.
     */
    @Select("""
            <script>
            SELECT COALESCE(SUM(delta), 0)
              FROM points_log
             WHERE user_id = #{userId}
               AND delete_state = 0
               AND delta > 0
               AND source_type IN
               <foreach collection="sources" item="source" open="(" separator="," close=")">
                   #{source}
               </foreach>
            </script>
            """)
    Integer sumPositiveBySources(@Param("userId") Long userId,
                                 @Param("sources") Byte[] sources);

    /**
     * 按负向来源统计消费绝对值总额.
     */
    @Select("""
            <script>
            SELECT COALESCE(SUM(-delta), 0)
              FROM points_log
             WHERE user_id = #{userId}
               AND delete_state = 0
               AND delta &lt; 0
               AND source_type IN
               <foreach collection="sources" item="source" open="(" separator="," close=")">
                   #{source}
               </foreach>
            </script>
            """)
    Integer sumNegativeAbsBySources(@Param("userId") Long userId,
                                    @Param("sources") Byte[] sources);
}
