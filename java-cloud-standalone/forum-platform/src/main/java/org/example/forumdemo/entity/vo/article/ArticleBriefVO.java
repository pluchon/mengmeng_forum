package org.example.forumdemo.entity.vo.article;

import lombok.Data;

import java.util.Date;

// 帖子列表项（不含逻辑删除等内部字段）
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
    // 帖子业务类型
    private Byte articleType;
    // 问答解决状态
    private Byte questionStatus;
    // 最佳答案对应的一级回答 ID
    private Long acceptedReplyId;
    private Byte status;
    private Byte state;
    private Date createTime;
    private Date updateTime;
}
