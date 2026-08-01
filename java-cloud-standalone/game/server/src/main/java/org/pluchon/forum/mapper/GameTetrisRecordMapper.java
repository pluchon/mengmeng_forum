package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.GameTetrisRecord;

// 俄罗斯方块局记录 Mapper
@Mapper
public interface GameTetrisRecordMapper extends BaseMapper<GameTetrisRecord> {
}
