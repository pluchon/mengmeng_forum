package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.ForumUserAiProfileSnapshot;

// 用户推荐画像快照数据访问
@Mapper
public interface ForumUserAiProfileSnapshotMapper extends BaseMapper<ForumUserAiProfileSnapshot> {
}
