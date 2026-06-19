package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.forumdemo.entity.db.CheckinStreakReward;

/**
 * 连续签到奖励 Mapper
 */
@Mapper
public interface CheckinStreakRewardMapper extends BaseMapper<CheckinStreakReward> {
}
