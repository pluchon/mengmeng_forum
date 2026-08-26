package org.pluchon.forum.entity.vo.article;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

import java.util.List;

// 作者代码水平一般，难免难看，请见谅
// 只用来展示用户点赞过的帖子的预览，具体详情要点进去看
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleListByLikeResponse {
    private Article article;
    private UserBriefVO user;
}
