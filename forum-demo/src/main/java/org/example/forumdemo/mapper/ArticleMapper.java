package org.example.forumdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.forumdemo.entity.db.Article;

import java.util.Map;

/**
 * @author pluchon
 * @create 2026-03-05-13:56
 * 作者代码水平一般，难免难看，请见谅
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /** 未软删帖子的浏览/点赞/收藏合计（用于工作台累计互动） */
    @Select("SELECT COALESCE(SUM(visit_count), 0) AS visitSum, "
            + "COALESCE(SUM(like_count), 0) AS likeSum, "
            + "COALESCE(SUM(favorite_count), 0) AS favoriteSum "
            + "FROM article WHERE delete_state <> 1")
    Map<String, Object> sumEngagementForWorkbench();
}
