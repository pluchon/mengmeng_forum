package org.example.forumdemo.entity.vo.recommendation;

import lombok.Data;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.vo.user.UserBriefVO;

// 推荐流中的帖子卡片
@Data
public class RecommendArticleVO {

    // 作者公开信息
    private UserBriefVO user;

    // 帖子公开信息
    private Article article;

    // 是否来自已关注作者
    private Boolean fromFollowing;

    // 推荐理由类型
    private String recommendReasonType;

    // 推荐理由文案
    private String recommendReason;
}
