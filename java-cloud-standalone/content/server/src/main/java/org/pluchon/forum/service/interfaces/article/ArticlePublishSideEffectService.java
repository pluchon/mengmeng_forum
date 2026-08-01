package org.pluchon.forum.service.interfaces.article;

// 帖子发布/下线时的计数、热榜、搜索与摘要缓存联动
public interface ArticlePublishSideEffectService {

    void rollbackPublishedExposure(Long articleId, Long boardId, Long userId);

    void promotePublishedExposure(Long articleId, Long userId, Long boardId);
}
