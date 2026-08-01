package org.pluchon.forum.entity.vo.recommendation;

import lombok.Data;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

// 推荐流中的帖子卡片
@Data
public class RecommendArticleVO {

    // 作者公开信息
    private UserBriefVO user;

    // 帖子公开信息
    private Article article;

}
