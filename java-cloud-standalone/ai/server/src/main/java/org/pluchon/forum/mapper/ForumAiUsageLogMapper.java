package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.pluchon.forum.entity.db.ForumAiUsageLog;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface ForumAiUsageLogMapper extends BaseMapper<ForumAiUsageLog> {

    @Select("SELECT model_code AS modelCode, "
            + "COALESCE(SUM(input_tokens), 0) AS inputTokens, "
            + "COALESCE(SUM(output_tokens), 0) AS outputTokens, "
            + "COUNT(*) AS callCount "
            + "FROM forum_ai_usage_log "
            + "WHERE user_id = #{userId} AND delete_state = 0 "
            + "AND create_time >= #{start} AND create_time < #{end} "
            + "GROUP BY model_code")
    List<Map<String, Object>> sumTokensByModelBetween(@Param("userId") Long userId,
                                                      @Param("start") Date start,
                                                      @Param("end") Date end);

    @Select("SELECT COUNT(*) FROM forum_ai_usage_log "
            + "WHERE user_id = #{userId} AND delete_state = 0 "
            + "AND create_time >= #{start} AND create_time < #{end}")
    int countCallsBetween(@Param("userId") Long userId,
                          @Param("start") Date start,
                          @Param("end") Date end);

    @Select("SELECT COALESCE(SUM(cost_yuan), 0) FROM forum_ai_usage_log "
            + "WHERE user_id = #{userId} AND billable_state = 1 AND image_count = 0 AND delete_state = 0 "
            + "AND create_time >= #{start} AND create_time < #{end}")
    java.math.BigDecimal sumBillableTextCostBetween(@Param("userId") Long userId,
                                                    @Param("start") Date start,
                                                    @Param("end") Date end);

    @Select("SELECT COALESCE(SUM(image_count), 0) FROM forum_ai_usage_log "
            + "WHERE user_id = #{userId} AND billable_state = 1 AND model_code = #{modelCode} AND delete_state = 0 "
            + "AND create_time >= #{start} AND create_time < #{end}")
    int sumImageCountByModelBetween(@Param("userId") Long userId,
                                    @Param("modelCode") String modelCode,
                                    @Param("start") Date start,
                                    @Param("end") Date end);
}
