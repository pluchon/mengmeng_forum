package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

/**
 * 管理端首页「通知公告」卡片预览行。
 */
@Data
public class AdminDashboardNoticePreviewVO {

    private Long id;

    private String title;

    private String subtitle;

    /** 东八区 yyyy-MM-dd HH:mm:ss */
    private String updateTime;

    private int pinTop;

    private int noticeKind;
}
