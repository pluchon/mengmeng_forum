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

    @Update("UPDATE ai_usage_daily SET cover_hint_used = cover_hint_used + 1 "
            + "WHERE user_id = #{userId} AND usage_date = #{usageDate} AND delete_state = 0")
    void incrementCoverHint(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);
}
