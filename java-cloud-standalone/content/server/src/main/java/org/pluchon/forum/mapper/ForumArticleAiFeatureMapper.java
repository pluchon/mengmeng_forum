package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.ForumArticleAiFeature;

// 帖子推荐特征数据访问
@Mapper
public interface ForumArticleAiFeatureMapper extends BaseMapper<ForumArticleAiFeature> {
}
