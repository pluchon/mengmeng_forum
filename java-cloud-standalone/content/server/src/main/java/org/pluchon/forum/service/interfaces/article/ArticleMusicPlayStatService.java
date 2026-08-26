package org.pluchon.forum.service.interfaces.article;

// 歌曲全站播放统计
public interface ArticleMusicPlayStatService {

    void incrementPlayCount(String musicKey);
}
