package org.pluchon.forum.entity.vo;

import lombok.Data;

// 用户端「公告中心」弹窗：已发布公告条目 直查库，无缓存
@Data
public class ForumNoticeCenterItemVO {

    private String id;

    private Integer noticeKind;

    private String categoryScope;

    private String templateId;

    private String sidebarKey;

    private String title;

    private String subtitle;

    private String contentMarkdown;

    // 模板扩展 JSON 原文，前端解析 highlights / coverImageUrl
    private String bodyJson;

    // 最近更新时间 东八区 ，便于用户端区分是否已更新
    private String updateTime;
}
