package org.pluchon.forum.entity.vo;

import lombok.Data;
import org.pluchon.forum.entity.vo.article.ArticleBriefVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

// 看板娘相关帖子展示项
@Data
public class MascotRelatedRecommendationItemVO {

    private ArticleBriefVO article;
    private UserBriefVO author;
    private String selectionReason;
}
