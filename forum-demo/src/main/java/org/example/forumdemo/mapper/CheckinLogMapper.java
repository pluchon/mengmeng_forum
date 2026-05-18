package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.forumdemo.entity.db.CheckinLog;

/**
 * 签到流水 Mapper
 */
@Mapper
public interface CheckinLogMapper extends BaseMapper<CheckinLog> {
}
