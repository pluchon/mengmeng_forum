package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.pluchon.forum.entity.db.LotteryActivityPrize;
import org.pluchon.forum.entity.vo.lottery.LotteryPrizePoolRow;

import java.util.List;

@Mapper
public interface LotteryActivityPrizeMapper extends BaseMapper<LotteryActivityPrize> {

    @Update("UPDATE lottery_activity_prize SET stock_remaining = stock_remaining - 1 "
            + "WHERE id = #{id} AND delete_state = 0 AND stock_remaining > 0")
    int decrementStockIfPositive(@Param("id") Long id);

    @Select("SELECT lap.id AS activityPrizeId, lap.prize_id AS prizeId, lap.weight AS weight, "
            + "lap.stock_remaining AS stockRemaining, lap.is_jackpot AS isJackpot, "
            + "p.name AS prizeName, p.prize_type AS prizeType, p.prize_value AS prizeValue, "
            + "p.is_mystery_bundle AS isMysteryBundle, p.catalog_status AS catalogStatus, "
            + "COALESCE(lap.image_path, p.image_path) AS imagePath "
            + "FROM lottery_activity_prize lap INNER JOIN lottery_prize p ON lap.prize_id = p.id "
            + "WHERE lap.activity_id = #{activityId} AND lap.delete_state = 0 AND p.delete_state = 0")
    List<LotteryPrizePoolRow> selectPool(@Param("activityId") Long activityId);

    // 用户抽奖/前台奖池展示：仅上架奖品 catalog_status 1
    @Select("SELECT lap.id AS activityPrizeId, lap.prize_id AS prizeId, lap.weight AS weight, "
            + "lap.stock_remaining AS stockRemaining, lap.is_jackpot AS isJackpot, "
            + "p.name AS prizeName, p.prize_type AS prizeType, p.prize_value AS prizeValue, "
            + "p.is_mystery_bundle AS isMysteryBundle, p.catalog_status AS catalogStatus, "
            + "COALESCE(lap.image_path, p.image_path) AS imagePath "
            + "FROM lottery_activity_prize lap INNER JOIN lottery_prize p ON lap.prize_id = p.id "
            + "WHERE lap.activity_id = #{activityId} AND lap.delete_state = 0 AND p.delete_state = 0 "
            + "AND p.catalog_status = 1")
    List<LotteryPrizePoolRow> selectDrawablePool(@Param("activityId") Long activityId);
}
