package org.pluchon.forum.service.interfaces.article;

public interface ArticleVideoDanmakuLikeService {

    void likeDanmaku(Long danmakuId, Long userId);

    void unlikeDanmaku(Long danmakuId, Long userId);
}
