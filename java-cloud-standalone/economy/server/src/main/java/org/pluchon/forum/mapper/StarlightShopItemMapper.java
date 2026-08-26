package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.StarlightShopItem;

@Mapper
public interface StarlightShopItemMapper extends BaseMapper<StarlightShopItem> {

    @Select("SELECT * FROM starlight_shop_item WHERE id = #{id} AND delete_state = 0 FOR UPDATE")
    StarlightShopItem selectByIdForUpdate(@Param("id") Long id);

    // 限量库存扣减；不限量 1 不改动
    @Update("UPDATE starlight_shop_item SET stock_remaining = stock_remaining - 1 "
            + "WHERE id = #{id} AND delete_state = 0 AND enabled = 1 "
            + "AND stock_remaining > 0")
    int deductStockOne(@Param("id") Long id);
}
