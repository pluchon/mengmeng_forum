package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

@Data
public class AdminForumNoticeRowVO {

    private String id;

    private Integer noticeKind;

    private String categoryScope;

    private String templateId;

    private String sidebarKey;

    private String title;

    private String subtitle;

    /** 列表展示：正文前若干字 */
    private String contentPreview;

    /** 列表展示用 body 摘要 */
    private String bodyPreview;

    private Integer pinTop;

    private Integer sort;

    private Integer publishState;

    private Integer deleteState;

    private String createTime;

    private String updateTime;
}
