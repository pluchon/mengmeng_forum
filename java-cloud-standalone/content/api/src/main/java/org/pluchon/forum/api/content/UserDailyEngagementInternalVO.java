package org.pluchon.forum.api.content;

import lombok.Data;

// 用户当日互动计数 抽奖任务进度裁决
@Data
public class UserDailyEngagementInternalVO {

    private Long userId;

    // 今日一级评论 + 楼中楼评论数
    private Integer commentCount;

    // 今日帖子点赞 + 评论点赞 + 楼中楼点赞数
    private Integer likeCount;
}
