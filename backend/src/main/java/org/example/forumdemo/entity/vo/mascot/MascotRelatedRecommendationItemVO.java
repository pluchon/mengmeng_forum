package org.example.forumdemo.entity.vo.mascot;

import lombok.Data;
import org.example.forumdemo.entity.vo.article.ArticleBriefVO;
import org.example.forumdemo.entity.vo.user.UserBriefVO;

// 看板娘相关帖子展示项
@Data
public class MascotRelatedRecommendationItemVO {

    private ArticleBriefVO article;
    private UserBriefVO author;
    private String selectionReason;
}
