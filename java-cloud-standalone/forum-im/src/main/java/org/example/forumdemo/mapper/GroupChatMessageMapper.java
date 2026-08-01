package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.forumdemo.entity.db.GroupChatMessage;

@Mapper
public interface GroupChatMessageMapper extends BaseMapper<GroupChatMessage> {
}
