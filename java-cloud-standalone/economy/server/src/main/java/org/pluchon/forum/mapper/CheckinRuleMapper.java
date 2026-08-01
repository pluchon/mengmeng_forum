package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.CheckinRule;

/**
 * 签到积分规则 Mapper
 */
@Mapper
public interface CheckinRuleMapper extends BaseMapper<CheckinRule> {
}
