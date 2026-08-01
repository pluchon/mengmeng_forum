package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.pluchon.forum.entity.db.LotteryDrawRecord;
import org.pluchon.forum.entity.vo.lottery.LotteryRecentDrawVO;

import java.util.List;

@Mapper
public interface LotteryDrawRecordMapper extends BaseMapper<LotteryDrawRecord> {

    @Select("SELECT prize_name AS prizeName, "
            + "IF(draw_batch_key IS NOT NULL AND draw_batch_key <> '', 1, 0) AS multiDraw "
            + "FROM lottery_draw_record "
            + "WHERE user_id = #{userId} AND activity_id = #{activityId} AND delete_state = 0 "
            + "ORDER BY id DESC LIMIT #{limit}")
    List<LotteryRecentDrawVO> selectRecentForUser(@Param("userId") Long userId,
                                                  @Param("activityId") Long activityId,
                                                  @Param("limit") int limit);

    @Select("SELECT COUNT(DISTINCT user_id) FROM lottery_draw_record "
            + "WHERE activity_id = #{activityId} AND delete_state = 0")
    long countDistinctDrawUsers(@Param("activityId") Long activityId);

    @Select("SELECT * FROM lottery_draw_record "
            + "WHERE user_id = #{userId} AND draw_batch_key = #{batchKey} AND delete_state = 0 "
            + "ORDER BY id ASC")
    List<LotteryDrawRecord> selectByUserAndBatchKey(@Param("userId") Long userId,
                                                    @Param("batchKey") String batchKey);
}
