package org.example.forumdemo.entity.vo.driftbottle;

import lombok.Data;

import java.util.Date;

// 漂流瓶匿名评论响应
@Data
public class DriftBottleCommentVO {

    // 评论 ID
    private Long id;

    // 匿名展示名
    private String anonymousName;

    // 评论内容
    private String content;

    // 是否我的评论
    private Boolean isMine;

    // 创建时间
    private Date createTime;
}
