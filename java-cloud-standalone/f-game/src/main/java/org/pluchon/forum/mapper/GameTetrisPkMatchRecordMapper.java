package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.GameTetrisPkMatchRecord;

// 俄罗斯方块 PK 对局记录 Mapper
@Mapper
public interface GameTetrisPkMatchRecordMapper extends BaseMapper<GameTetrisPkMatchRecord> {
}
