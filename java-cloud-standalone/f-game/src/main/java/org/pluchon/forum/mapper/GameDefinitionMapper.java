package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.GameDefinition;

@Mapper
public interface GameDefinitionMapper extends BaseMapper<GameDefinition> {
}
