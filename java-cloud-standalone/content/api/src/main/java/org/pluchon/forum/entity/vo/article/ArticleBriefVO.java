package org.pluchon.forum.entity.vo.article;

import lombok.Data;

import java.util.Date;

// 帖子列表项 不含逻辑删除等内部字段
@Data
public class ArticleBriefVO {

    private Long id;
    private Long boardId;
    private Long userId;
    private String title;
    private String content;
    private Integer visitCount;
    private Integer replyCount;
    private Integer likeCount;
    private String coverImg;
    private Byte mediaType;
    private String videoUrl;
    private Integer favoriteCount;
    private Byte articleType;
    private Byte questionStatus;
    private Long acceptedReplyId;
    private Byte status;
    private Byte state;
    // 最近一次审核结论。创作中心的帖子卡片要直接把拒绝理由摆出来，
    // 否则用户只看到"审核未通过"却不知道该改什么
    private String auditResultMessage;
    private Date createTime;
    private Date updateTime;
}
