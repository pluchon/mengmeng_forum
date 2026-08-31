package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.pluchon.forum.entity.db.UserBagItem;

@Mapper
public interface UserBagItemMapper extends BaseMapper<UserBagItem> {

    /** 使用时加行锁，避免并发点击把同一件用两次 */
    @Select("SELECT * FROM user_bag_item WHERE id = #{id} FOR UPDATE")
    UserBagItem selectByIdForUpdate(@Param("id") Long id);
}
