package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.ContentAiTask;

// 内容域AI任务数据访问
@Mapper
public interface ContentAiTaskMapper extends BaseMapper<ContentAiTask> {
}
