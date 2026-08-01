package org.pluchon.forum.api.content;

import lombok.Data;

import java.util.Date;

// 帖子内部视图：跨域回表用，含热分计算所需计数
@Data
public class ArticleInternalVO {

    private Long id;
    private Long boardId;
    private Long userId;
    private String title;
    private String content;
    private Integer visitCount;
    private Integer replyCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer subReplyCount;
    private String coverImg;
    private Byte mediaType;
    private String videoUrl;
    private Byte articleType;
    private Byte questionStatus;
    private Long acceptedReplyId;
    private Byte status;
    private Byte state;
    private Byte deleteState;
    private Date createTime;
    private Date updateTime;
}
