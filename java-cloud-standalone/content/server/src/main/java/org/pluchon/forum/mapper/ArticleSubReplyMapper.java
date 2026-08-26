package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.ArticleSubReply;

// 楼中楼回复 Mapper
@Mapper
public interface ArticleSubReplyMapper extends BaseMapper<ArticleSubReply> {
}
