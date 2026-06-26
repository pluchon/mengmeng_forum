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
    private Byte status;
    private Byte state;
    private Date createTime;
    private Date updateTime;
}
