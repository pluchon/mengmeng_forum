package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台工作台汇总：帖子数、用户与互动统计、公告预览、AI 调用趋势、当前登录管理员展示信息。
 */
@Data
public class AdminWorkbenchVO {

    /** 未软删的帖子总数 */
    private long articleCount;

    /** 未删除用户总数（含管理员与普通账号） */
    private long totalUserCount;

    /** 未删除且非管理员账号数（注册会员口径） */
    private long memberUserCount;

    /**
     * 累计互动（加权）：全站帖子浏览量×30% + 点赞量×40% + 评论（一级+楼中楼）×10% + 收藏量×20%。
     */
    private long interactionCount;

    private String nickname;

    private String avatar;

    private List<AdminDashboardNoticePreviewVO> noticePreview = new ArrayList<>();

    private AdminAiTrendsBundleVO aiUsageTrends;

    private AdminLotteryTrendVO lotteryDrawTrend;
}
