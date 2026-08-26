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

    @Select("SELECT id, user_id AS userId, prize_name AS prizeName, prize_type AS prizeType, "
            + "create_time AS createTime "
            + "FROM lottery_draw_record "
            + "WHERE activity_id = #{activityId} AND delete_state = 0 "
            + "AND prize_type <> 0 "
            + "ORDER BY id DESC LIMIT #{limit}")
    List<LotteryDrawRecord> selectRecentPublicByActivity(@Param("activityId") Long activityId,
                                                         @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM lottery_draw_record "
            + "WHERE activity_id = #{activityId} AND delete_state = 0 "
            + "AND prize_type <> 0")
    long countPublicByActivity(@Param("activityId") Long activityId);

    @Select("SELECT COUNT(DISTINCT user_id) FROM lottery_draw_record "
            + "WHERE activity_id = #{activityId} AND delete_state = 0")
    long countDistinctDrawUsers(@Param("activityId") Long activityId);

    @Select("SELECT * FROM lottery_draw_record "
            + "WHERE user_id = #{userId} AND draw_batch_key = #{batchKey} AND delete_state = 0 "
            + "ORDER BY id ASC")
    List<LotteryDrawRecord> selectByUserAndBatchKey(@Param("userId") Long userId,
                                                    @Param("batchKey") String batchKey);

    @Select("""
            <script>
            SELECT * FROM lottery_draw_record
             WHERE user_id = #{userId}
               AND delete_state = 0
               AND draw_batch_key IN
               <foreach collection="batchKeys" item="batchKey" open="(" separator="," close=")">
                 #{batchKey}
               </foreach>
             ORDER BY id ASC
            </script>
            """)
    List<LotteryDrawRecord> selectByUserAndBatchKeys(@Param("userId") Long userId,
                                                      @Param("batchKeys") List<String> batchKeys);
}
