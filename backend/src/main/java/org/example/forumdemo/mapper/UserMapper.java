package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.forumdemo.entity.db.User;

import java.util.Date;

/**
 * @author pluchon
 * @create 2026-03-05-13:58
 * 作者代码水平一般，难免难看，请见谅
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 原子加积分. amount 必须为正数, 调用方负责校验.
     * @return 影响行数, 应为 1
     */
    @Update("UPDATE user SET points = points + #{amount} WHERE id = #{userId} AND delete_state = 0")
    int addPoints(@Param("userId") Long userId, @Param("amount") int amount);

    /**
     * 原子扣积分, 余额不足时直接 0 行返回, 上层据此判断 FAILED_POINTS_NOT_ENOUGH.
     * amount 必须为正数, 调用方负责校验.
     * @return 影响行数, 1=扣成功, 0=余额不足或用户不存在
     */
    @Update("UPDATE user SET points = points - #{amount} "
            + "WHERE id = #{userId} AND delete_state = 0 AND points >= #{amount}")
    int deductPoints(@Param("userId") Long userId, @Param("amount") int amount);

    @Update("UPDATE user SET vip_tier = #{tier}, vip_expire_at = #{expireAt} "
            + "WHERE id = #{userId} AND delete_state = 0")
    int updateVipSubscription(@Param("userId") Long userId, @Param("tier") Byte tier, @Param("expireAt") Date expireAt);

    /**
     * 抽奖事务内锁定用户行，避免并发抽奖绕过保底计数。
     */
    @Select("SELECT * FROM user WHERE id = #{userId} AND delete_state = 0 FOR UPDATE")
    User selectByIdForUpdate(@Param("userId") Long userId);

    @Update("UPDATE user SET lottery_pity_draws = 0 WHERE id = #{userId} AND delete_state = 0")
    int resetLotteryPityDraws(@Param("userId") Long userId);

    @Update("UPDATE user SET lottery_pity_draws = lottery_pity_draws + 1 WHERE id = #{userId} AND delete_state = 0")
    int incrementLotteryPityDraws(@Param("userId") Long userId);

    /** @return 1 表示由未领取更新为已领取 */
    @Update("UPDATE user SET lottery_surprise_claimed = 1 WHERE id = #{userId} AND delete_state = 0 "
            + "AND COALESCE(lottery_surprise_claimed, 0) = 0")
    int markLotterySurpriseClaimed(@Param("userId") Long userId);
}
