package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

@Data
public class AdminArticleRowVO {
    private String id;
    private String title;
    private String boardId;
    private String boardName;
    private String userId;
    private String username;
    private String nickname;
    private Integer status;
    private Integer state;
    private Integer deleteState;
    private Integer visitCount;
    private Integer replyCount;
    private String createTime;
}
