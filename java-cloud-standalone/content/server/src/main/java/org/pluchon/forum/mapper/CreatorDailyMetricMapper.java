package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.pluchon.forum.entity.db.CreatorDailyMetric;

import java.util.Date;

@Mapper
public interface CreatorDailyMetricMapper extends BaseMapper<CreatorDailyMetric> {

    // 原子累加创作者某日指标，确保并发阅读和点赞不会丢失
    @Insert("INSERT INTO creator_daily_metric (user_id, stat_date, read_count, like_count, publish_count, delete_state) "
            + "VALUES (#{userId}, #{statDate}, #{readDelta}, #{likeDelta}, #{publishDelta}, 0) "
            + "ON DUPLICATE KEY UPDATE "
            + "read_count = GREATEST(read_count + VALUES(read_count), 0), "
            + "like_count = GREATEST(like_count + VALUES(like_count), 0), "
            + "publish_count = GREATEST(publish_count + VALUES(publish_count), 0), "
            + "update_time = CURRENT_TIMESTAMP")
    void increment(@Param("userId") Long userId,
                  @Param("statDate") Date statDate,
                  @Param("readDelta") int readDelta,
                  @Param("likeDelta") int likeDelta,
                  @Param("publishDelta") int publishDelta);
}
