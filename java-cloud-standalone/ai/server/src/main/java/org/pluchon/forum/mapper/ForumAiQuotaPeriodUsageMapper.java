package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.ForumAiQuotaPeriodUsage;

@Mapper
public interface ForumAiQuotaPeriodUsageMapper extends BaseMapper<ForumAiQuotaPeriodUsage> {

    @Insert("INSERT IGNORE INTO forum_ai_quota_period_usage "
            + "(user_id, quota_period_key, delete_state) VALUES (#{userId}, #{periodKey}, 0)")
    void ensurePeriod(@Param("userId") Long userId, @Param("periodKey") String periodKey);

    @Update("UPDATE forum_ai_quota_period_usage SET qwen_reserved_micros = qwen_reserved_micros + #{amount} "
            + "WHERE user_id = #{userId} AND quota_period_key = #{periodKey} AND delete_state = 0 "
            + "AND qwen_reserved_micros + IFNULL(qwen_used_micros, 0) + #{amount} <= #{limitMicros}")
    int reserveQwen(@Param("userId") Long userId, @Param("periodKey") String periodKey,
                    @Param("amount") long amount, @Param("limitMicros") long limitMicros);

    // units：普通档 1，进阶档 2（wan2.7-image-pro 单价是普通档的 2.5 倍，额度按两张算）
    @Update("UPDATE forum_ai_quota_period_usage SET wan_reserved_count = wan_reserved_count + #{units} "
            + "WHERE user_id = #{userId} AND quota_period_key = #{periodKey} AND delete_state = 0 "
            + "AND wan_reserved_count + IFNULL(wan_used_count, 0) + #{units} <= #{limitCount}")
    int reserveWan(@Param("userId") Long userId, @Param("periodKey") String periodKey,
                   @Param("units") int units, @Param("limitCount") int limitCount);

    // 额度重置卡：只清已结算用量，预占代表在途请求不能动，否则会超卖
    @Update("UPDATE forum_ai_quota_period_usage SET qwen_used_micros = 0, wan_used_count = 0 "
            + "WHERE user_id = #{userId} AND quota_period_key = #{periodKey} AND delete_state = 0")
    int resetUsage(@Param("userId") Long userId, @Param("periodKey") String periodKey);

    @Select("SELECT * FROM forum_ai_quota_period_usage "
            + "WHERE user_id = #{userId} AND quota_period_key = #{periodKey} AND delete_state = 0")
    ForumAiQuotaPeriodUsage selectByUserAndPeriod(@Param("userId") Long userId,
                                                 @Param("periodKey") String periodKey);

    @Update("UPDATE forum_ai_quota_period_usage SET qwen_reserved_micros = "
            + "GREATEST(qwen_reserved_micros - #{amount}, 0) "
            + "WHERE user_id = #{userId} AND quota_period_key = #{periodKey} AND delete_state = 0")
    void releaseQwen(@Param("userId") Long userId, @Param("periodKey") String periodKey,
                    @Param("amount") long amount);

    @Update("UPDATE forum_ai_quota_period_usage SET wan_reserved_count = GREATEST(wan_reserved_count - #{units}, 0) "
            + "WHERE user_id = #{userId} AND quota_period_key = #{periodKey} AND delete_state = 0")
    void releaseWan(@Param("userId") Long userId, @Param("periodKey") String periodKey,
                    @Param("units") int units);

    @Update("UPDATE forum_ai_quota_period_usage SET "
            + "qwen_reserved_micros = GREATEST(qwen_reserved_micros - #{qwenReserved}, 0), "
            + "qwen_used_micros = qwen_used_micros + #{qwenActual}, "
            + "wan_reserved_count = GREATEST(wan_reserved_count - #{wanCount}, 0), "
            + "wan_used_count = wan_used_count + #{wanCount} "
            + "WHERE user_id = #{userId} AND quota_period_key = #{periodKey} AND delete_state = 0")
    void settle(@Param("userId") Long userId, @Param("periodKey") String periodKey,
               @Param("qwenReserved") long qwenReserved, @Param("qwenActual") long qwenActual,
               @Param("wanCount") int wanCount);
}
