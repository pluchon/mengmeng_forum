package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.vo.article.MusicTrackVO;

import java.util.List;

// 音乐大厅推荐片单：缓存读写
public interface ArticleMusicRecommendSlateService {

    // 读取未过期片单对应曲目；无缓存返回空列表
    List<MusicTrackVO> loadActiveTracks(Long userId);

    // 覆盖写入本期片单（最多 30 首）
    void saveSlate(Long userId, String periodKey, String source, List<String> musicKeys);

    // 当前双周周期键，如 2026-W34
    String currentPeriodKey();
}
