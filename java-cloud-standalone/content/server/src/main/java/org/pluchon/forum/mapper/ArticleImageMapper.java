package org.pluchon.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.pluchon.forum.entity.db.ArticleImage;

@Mapper
public interface ArticleImageMapper extends BaseMapper<ArticleImage> {
}
