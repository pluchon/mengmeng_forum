package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.forumdemo.entity.db.ArticleReply;

/**
 * @author pluchon
 * @create 2026-03-05-13:56
 *         作者代码水平一般，难免难看，请见谅
 */
@Mapper
public interface ArticleReplyMapper extends BaseMapper<ArticleReply> {
}
