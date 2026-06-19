package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.forumdemo.entity.db.GameMatchRecord;

@Mapper
public interface GameMatchRecordMapper extends BaseMapper<GameMatchRecord> {
}
