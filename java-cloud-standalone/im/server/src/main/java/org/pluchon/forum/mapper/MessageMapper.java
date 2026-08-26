package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.Message;

// 作者代码水平一般，难免难看，请见谅
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
