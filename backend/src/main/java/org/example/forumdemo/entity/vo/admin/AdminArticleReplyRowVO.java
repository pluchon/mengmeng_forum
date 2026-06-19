package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

@Data
public class AdminArticleReplyRowVO {
    private String id;
    private String articleId;
    private String postUserId;
    private String username;
    private String nickname;
    private String contentPreview;
    private Integer state;
    private Integer deleteState;
    private String createTime;
}
