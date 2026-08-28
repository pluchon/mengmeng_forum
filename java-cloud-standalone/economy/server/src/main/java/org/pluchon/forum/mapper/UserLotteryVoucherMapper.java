package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.UserLotteryVoucher;

@Mapper
public interface UserLotteryVoucherMapper extends BaseMapper<UserLotteryVoucher> {

    @Select("SELECT * FROM user_lottery_voucher WHERE user_id = #{userId} AND delete_state = 0 FOR UPDATE")
    UserLotteryVoucher selectByUserIdForUpdate(@Param("userId") Long userId);

    @Select("SELECT * FROM user_lottery_voucher WHERE user_id = #{userId} AND delete_state = 0 LIMIT 1")
    UserLotteryVoucher selectByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO user_lottery_voucher (user_id, balance, version, delete_state) VALUES (#{userId}, 0, 0, 0)")
    void insertWallet(@Param("userId") Long userId);

    @Update("UPDATE user_lottery_voucher SET balance = #{balance}, version = version + 1 "
            + "WHERE user_id = #{userId} AND version = #{version} AND delete_state = 0")
    int updateBalanceOptimistic(@Param("userId") Long userId,
                                @Param("balance") int balance,
                                @Param("version") int version);
}
