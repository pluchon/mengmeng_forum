package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.UserVipSubscription;

import java.util.Date;

// VIP 订阅 Mapper economy
@Mapper
public interface UserVipSubscriptionMapper extends BaseMapper<UserVipSubscription> {

    @Select("SELECT * FROM user_vip_subscription WHERE user_id = #{userId} AND delete_state = 0 LIMIT 1")
    UserVipSubscription selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_vip_subscription WHERE user_id = #{userId} AND delete_state = 0 LIMIT 1 FOR UPDATE")
    UserVipSubscription selectByUserIdForUpdate(@Param("userId") Long userId);

    @Insert("INSERT INTO user_vip_subscription (user_id, vip_tier, vip_expire_at, delete_state) "
            + "VALUES (#{userId}, #{tier}, #{expireAt}, 0)")
    int insertSubscription(@Param("userId") Long userId, @Param("tier") Byte tier, @Param("expireAt") Date expireAt);

    @Update("UPDATE user_vip_subscription SET vip_tier = #{tier}, vip_expire_at = #{expireAt} "
            + "WHERE user_id = #{userId} AND delete_state = 0")
    int updateSubscription(@Param("userId") Long userId, @Param("tier") Byte tier, @Param("expireAt") Date expireAt);

    @Update("UPDATE user_vip_subscription SET vip_tier = #{tier}, vip_expire_at = #{expireAt}, "
            + "base_quota_tier = #{baseQuotaTier}, quota_period_start = #{periodStart}, quota_period_end = #{periodEnd} "
            + "WHERE user_id = #{userId} AND delete_state = 0")
    int updatePaidSubscription(@Param("userId") Long userId,
                               @Param("tier") Byte tier,
                               @Param("expireAt") Date expireAt,
                               @Param("baseQuotaTier") Byte baseQuotaTier,
                               @Param("periodStart") Date periodStart,
                               @Param("periodEnd") Date periodEnd);

    @Update("UPDATE user_vip_subscription SET base_quota_tier = #{baseQuotaTier}, "
            + "quota_period_start = #{periodStart}, quota_period_end = #{periodEnd} "
            + "WHERE user_id = #{userId} AND delete_state = 0")
    int updateBaseQuotaPeriod(@Param("userId") Long userId,
                              @Param("baseQuotaTier") Byte baseQuotaTier,
                              @Param("periodStart") Date periodStart,
                              @Param("periodEnd") Date periodEnd);
}
