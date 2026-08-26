package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.User;

import java.util.Date;

// 作者代码水平一般，难免难看，请见谅
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE user SET vip_tier = #{tier}, vip_expire_at = #{expireAt} "
            + "WHERE id = #{userId} AND delete_state = 0")
    int updateVipSubscription(@Param("userId") Long userId, @Param("tier") Byte tier, @Param("expireAt") Date expireAt);

    // 抽奖事务内锁定用户行 兼容旧调用；保底计数权威在 economy.user_lottery_pity
    @Select("SELECT * FROM user WHERE id = #{userId} AND delete_state = 0 FOR UPDATE")
    User selectByIdForUpdate(@Param("userId") Long userId);

}
