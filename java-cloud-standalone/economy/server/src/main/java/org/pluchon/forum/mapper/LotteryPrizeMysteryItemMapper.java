package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.LotteryPrizeMysteryItem;

@Mapper
public interface LotteryPrizeMysteryItemMapper extends BaseMapper<LotteryPrizeMysteryItem> {

    /** 限量子奖项的 CAS 扣减；返回 0 表示已被别人抢走，调用方需重抽 */
    @Update("UPDATE lottery_prize_mystery_item SET stock_remaining = stock_remaining - 1 "
            + "WHERE id = #{id} AND stock_remaining > 0")
    int decrementStockIfPositive(@Param("id") Long id);
}
