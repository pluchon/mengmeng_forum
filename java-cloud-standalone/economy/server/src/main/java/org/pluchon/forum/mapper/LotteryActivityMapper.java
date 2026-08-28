package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.pluchon.forum.entity.db.LotteryActivity;

@Mapper
public interface LotteryActivityMapper extends BaseMapper<LotteryActivity> {

    @Select("SELECT * FROM lottery_activity WHERE delete_state = 0 AND status = 1 AND phase = 1 "
            + "AND (start_time IS NULL OR start_time <= NOW()) "
            + "AND (end_time IS NULL OR end_time >= NOW()) "
            + "ORDER BY id DESC LIMIT 1")
    LotteryActivity selectActiveOne();

}
