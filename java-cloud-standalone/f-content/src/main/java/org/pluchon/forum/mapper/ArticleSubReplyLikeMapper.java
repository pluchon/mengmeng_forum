package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.ArticleSubReplyLike;

@Mapper
public interface ArticleSubReplyLikeMapper extends BaseMapper<ArticleSubReplyLike> {
}
