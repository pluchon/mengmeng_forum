package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.AiUsageDaily;

import java.time.LocalDate;

@Mapper
public interface AiUsageDailyMapper extends BaseMapper<AiUsageDaily> {

    @Insert("INSERT INTO ai_usage_daily (user_id, usage_date, qwen_flash_used, advanced_llm_used, "
            + "image_normal_used, companion_normal_used, "
            + "cover_hint_used, delete_state) VALUES (#{userId}, #{usageDate}, 0, 0, 0, 0, 0, 0) "
            + "ON DUPLICATE KEY UPDATE user_id = user_id")
    void ensureUsageRow(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);

    @Update("UPDATE ai_usage_daily SET qwen_flash_used = qwen_flash_used + 1 "
            + "WHERE user_id = #{userId} AND usage_date = #{usageDate} AND delete_state = 0 "
            + "AND qwen_flash_used < #{cap}")
    int incrementQwenFlashIfBelow(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate,
                                   @Param("cap") int cap);

    @Update("UPDATE ai_usage_daily SET advanced_llm_used = advanced_llm_used + 1 "
            + "WHERE user_id = #{userId} AND usage_date = #{usageDate} AND delete_state = 0 "
            + "AND advanced_llm_used < #{cap}")
    int incrementAdvancedIfBelow(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate,
                                 @Param("cap") int cap);

    @Update("UPDATE ai_usage_daily SET image_normal_used = image_normal_used + 1 "
            + "WHERE user_id = #{userId} AND usage_date = #{usageDate} AND delete_state = 0 "
            + "AND image_normal_used < #{cap}")
    int incrementImageNormalIfBelow(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate,
                                    @Param("cap") int cap);

    @Update("UPDATE ai_usage_daily SET cover_hint_used = cover_hint_used + 1 "
            + "WHERE user_id = #{userId} AND usage_date = #{usageDate} AND delete_state = 0")
    int incrementCoverHint(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);

    @Update("UPDATE ai_usage_daily SET qwen_flash_used = qwen_flash_used - 1 "
            + "WHERE user_id = #{userId} AND usage_date = #{usageDate} AND delete_state = 0 "
            + "AND qwen_flash_used > 0")
    int decrementQwenFlash(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);

    @Update("UPDATE ai_usage_daily SET advanced_llm_used = advanced_llm_used - 1 "
            + "WHERE user_id = #{userId} AND usage_date = #{usageDate} AND delete_state = 0 "
            + "AND advanced_llm_used > 0")
    int decrementAdvanced(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);

    @Update("UPDATE ai_usage_daily SET image_normal_used = image_normal_used - 1 "
            + "WHERE user_id = #{userId} AND usage_date = #{usageDate} AND delete_state = 0 "
            + "AND image_normal_used > 0")
    int decrementImageNormal(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);
}
