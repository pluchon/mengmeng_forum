package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

@Data
public class AdminForumNoticeDetailVO {

    private String id;

    private Integer noticeKind;

    private String categoryScope;

    private String templateId;

    private String sidebarKey;

    private String title;

    private String subtitle;

    private String contentMarkdown;

    private String bodyJson;

    private Integer pinTop;

    private Integer sort;

    private Integer publishState;

    private Integer deleteState;

    private String createTime;

    private String updateTime;
}
