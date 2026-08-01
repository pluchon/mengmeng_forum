package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.GroupChatMessage;

@Mapper
public interface GroupChatMessageMapper extends BaseMapper<GroupChatMessage> {
}
