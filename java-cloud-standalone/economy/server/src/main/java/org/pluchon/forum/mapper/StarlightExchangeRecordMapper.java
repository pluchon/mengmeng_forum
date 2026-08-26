package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.StarlightExchangeRecord;

import java.util.Date;

@Mapper
public interface StarlightExchangeRecordMapper extends BaseMapper<StarlightExchangeRecord> {

    @Select("SELECT * FROM starlight_exchange_record WHERE id = #{id} AND delete_state = 0 FOR UPDATE")
    StarlightExchangeRecord selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM starlight_exchange_record "
            + "WHERE user_id = #{userId} AND item_id = #{itemId} AND delete_state = 0 "
            + "AND create_time >= #{dayStart} AND create_time < #{dayEnd}")
    int countUserItemBetween(@Param("userId") Long userId,
                             @Param("itemId") Long itemId,
                             @Param("dayStart") Date dayStart,
                             @Param("dayEnd") Date dayEnd);

    // 未使用 → 已使用；并发下仅一条成功
    @Update("UPDATE starlight_exchange_record SET use_status = 1, use_time = #{useTime} "
            + "WHERE id = #{id} AND user_id = #{userId} AND delete_state = 0 AND use_status = 0")
    int markUsed(@Param("id") Long id, @Param("userId") Long userId, @Param("useTime") Date useTime);
}
