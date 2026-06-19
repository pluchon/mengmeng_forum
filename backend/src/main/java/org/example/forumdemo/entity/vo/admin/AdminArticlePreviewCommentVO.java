package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

/** 管理端帖子预览：热度靠前的一级评论（不含楼中楼） */
@Data
public class AdminArticlePreviewCommentVO {

    private String nickname;

    private String avatarUrl;

    private String content;

    private Integer likeCount;
}
