package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.pluchon.forum.entity.db.VipQuotaBonusGrant;

import java.util.List;

@Mapper
public interface VipQuotaBonusGrantMapper extends BaseMapper<VipQuotaBonusGrant> {

    @Select("SELECT * FROM vip_quota_bonus_grant WHERE user_id = #{userId} "
            + "AND expire_time > NOW() AND delete_state = 0 ORDER BY expire_time ASC, id ASC FOR UPDATE")
    List<VipQuotaBonusGrant> selectActiveForUpdate(@Param("userId") Long userId);

    @Select("SELECT * FROM vip_quota_bonus_grant WHERE id = #{id} AND user_id = #{userId} "
            + "AND delete_state = 0 LIMIT 1 FOR UPDATE")
    VipQuotaBonusGrant selectByIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
