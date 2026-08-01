package org.example.forumdemo.entity.vo.article;

import lombok.Data;
import org.example.forumdemo.common.enums.HotArticleTrendDirection;
import org.example.forumdemo.entity.vo.user.UserBriefVO;

// 热帖榜分页列表项
@Data
public class HotArticleListItemVO {

    // 热帖榜全局排名
    private Long rank;

    // 帖子公开信息
    private ArticleBriefVO article;

    // 作者公开信息
    private UserBriefVO user;

    // 当前登录用户是否关注作者
    private Boolean fromFollowing;

    // 相对上一统计周期的热度方向
    private HotArticleTrendDirection trendDirection;
}
