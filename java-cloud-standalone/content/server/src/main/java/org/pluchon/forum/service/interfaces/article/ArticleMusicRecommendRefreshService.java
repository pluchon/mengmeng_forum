package org.pluchon.forum.service.interfaces.article;

// 双周音乐品味片单刷新（AI 优先，失败回落规则混排）
public interface ArticleMusicRecommendRefreshService {

    // 扫描近 28 天有收藏/播放的用户，写入本期片单
    void refreshBiweeklySlates();

    // 是否为本双周桶的执行周日（约每 14 天一次）
    boolean shouldRunBiweeklyRefresh();
}
