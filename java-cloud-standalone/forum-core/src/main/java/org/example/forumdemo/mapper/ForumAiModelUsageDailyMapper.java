package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.forumdemo.entity.db.ForumAiModelUsageDaily;

import java.sql.Date;

@Mapper
public interface ForumAiModelUsageDailyMapper extends BaseMapper<ForumAiModelUsageDaily> {

    @Insert("INSERT INTO forum_ai_model_usage_daily "
            + "(stat_date, model_code, call_count, points_spent, input_tokens, output_tokens, image_count) "
            + "VALUES (#{statDate}, #{modelCode}, 1, #{pointsSpent}, #{inputTokens}, #{outputTokens}, #{imageCount}) "
            + "ON DUPLICATE KEY UPDATE "
            + "call_count = call_count + 1, "
            + "points_spent = points_spent + #{pointsSpent}, "
            + "input_tokens = input_tokens + #{inputTokens}, "
            + "output_tokens = output_tokens + #{outputTokens}, "
            + "image_count = image_count + #{imageCount}")
    int incrementUsage(
            @Param("statDate") Date statDate,
            @Param("modelCode") String modelCode,
            @Param("pointsSpent") long pointsSpent,
            @Param("inputTokens") long inputTokens,
            @Param("outputTokens") long outputTokens,
            @Param("imageCount") int imageCount);
}
