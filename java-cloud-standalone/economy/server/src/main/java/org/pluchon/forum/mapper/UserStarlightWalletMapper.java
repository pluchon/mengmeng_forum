package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.UserStarlightWallet;

@Mapper
public interface UserStarlightWalletMapper extends BaseMapper<UserStarlightWallet> {

    @Select("SELECT * FROM user_starlight_wallet WHERE user_id = #{userId} AND delete_state = 0 FOR UPDATE")
    UserStarlightWallet selectByUserIdForUpdate(@Param("userId") Long userId);

    @Select("SELECT * FROM user_starlight_wallet WHERE user_id = #{userId} AND delete_state = 0 LIMIT 1")
    UserStarlightWallet selectByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO user_starlight_wallet (user_id, balance, version, delete_state) VALUES (#{userId}, 0, 0, 0)")
    void insertWallet(@Param("userId") Long userId);

    @Update("UPDATE user_starlight_wallet SET balance = #{balance}, version = version + 1 "
            + "WHERE user_id = #{userId} AND version = #{version} AND delete_state = 0")
    int updateBalanceOptimistic(@Param("userId") Long userId,
                                @Param("balance") int balance,
                                @Param("version") int version);
}
