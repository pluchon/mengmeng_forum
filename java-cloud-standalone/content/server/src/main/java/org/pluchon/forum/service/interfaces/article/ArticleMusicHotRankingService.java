package org.pluchon.forum.service.interfaces.article;

import java.util.List;

// 音乐大厅本周热榜（Redis ZSet）
public interface ArticleMusicHotRankingService {

    void rebuildHotMusicRanking();

    void refreshTrackScore(String musicKey);

    void removeFromRanking(String musicKey);

    List<String> listHotMusicKeys(int offset, int limit);

    long countHotMusicKeys();
}
