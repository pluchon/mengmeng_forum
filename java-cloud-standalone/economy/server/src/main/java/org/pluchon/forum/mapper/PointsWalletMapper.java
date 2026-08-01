package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.PointsWallet;

// 积分钱包 Mapper：余额加减只改本表，不再写 user.points
@Mapper
public interface PointsWalletMapper extends BaseMapper<PointsWallet> {

    @Select("SELECT * FROM points_wallet WHERE user_id = #{userId} AND delete_state = 0 FOR UPDATE")
    PointsWallet selectByUserIdForUpdate(@Param("userId") Long userId);

    @Select("SELECT * FROM points_wallet WHERE user_id = #{userId} AND delete_state = 0 LIMIT 1")
    PointsWallet selectByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO points_wallet (user_id, balance, version, delete_state) "
            + "VALUES (#{userId}, #{balance}, 0, 0)")
    int insertWallet(@Param("userId") Long userId, @Param("balance") int balance);

    @Update("UPDATE points_wallet SET balance = balance + #{amount}, version = version + 1 "
            + "WHERE user_id = #{userId} AND delete_state = 0")
    int addBalance(@Param("userId") Long userId, @Param("amount") int amount);

    @Update("UPDATE points_wallet SET balance = balance - #{amount}, version = version + 1 "
            + "WHERE user_id = #{userId} AND delete_state = 0 AND balance >= #{amount}")
    int deductBalance(@Param("userId") Long userId, @Param("amount") int amount);
}
