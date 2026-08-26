package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.pluchon.forum.entity.db.Board;

// 作者代码水平一般，难免难看，请见谅
@Mapper
public interface BoardMapper extends BaseMapper<Board> {
}
