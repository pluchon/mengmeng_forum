package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.forumdemo.entity.db.CheckinRule;

/**
 * 签到积分规则 Mapper
 */
@Mapper
public interface CheckinRuleMapper extends BaseMapper<CheckinRule> {
}
