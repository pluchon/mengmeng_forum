package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端只读帖子预览（正文 + 相册图），不暴露编辑能力。
 */
@Data
public class AdminArticlePreviewVO {

    private String id;

    private String title;

    private String boardName;

    /** 所属分类名称 */
    private String categoryName;

    /** 0 富文本 1 Markdown */
    private Integer contentType;

    private String content;

    private String coverImg;

    private List<String> imageUrls = new ArrayList<>();

    private Integer status;

    private Integer state;

    private Integer deleteState;

    private String userId;

    private String username;

    private String nickname;

    private String authorAvatarUrl;

    /** 0 普通 1 PRO 2 MAX */
    private Integer authorVipTier;

    private String authorVipExpireAt;

    /** 点赞数最高的 10 条一级评论 */
    private List<AdminArticlePreviewCommentVO> topComments = new ArrayList<>();
}
