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

    // 相册图片数量 未删除 ，图文帖角标用
    private Integer imageCount;

    // 相册第一张图 URL，卡片悬停预览用；无相册时为空
    private String firstImageUrl;



}
