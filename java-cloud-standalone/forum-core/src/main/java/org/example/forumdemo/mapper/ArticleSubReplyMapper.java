package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.forumdemo.entity.db.ArticleSubReply;

/**
 * @author pluchon
 * 楼中楼回复 Mapper
 */
@Mapper
public interface ArticleSubReplyMapper extends BaseMapper<ArticleSubReply> {
}
