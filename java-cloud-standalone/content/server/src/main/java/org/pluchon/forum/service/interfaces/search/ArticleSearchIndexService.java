package org.pluchon.forum.service.interfaces.search;

import org.pluchon.forum.entity.db.Article;

import java.util.List;

public interface ArticleSearchIndexService {

    // 为已发布帖子建立/刷新倒排与正排
    void indexPublishedArticle(Article article, List<String> tagNames);

    // 按 ID 从库加载并同步索引 发布/重建用
    void syncPublishedArticle(Long articleId);

    // 从搜索索引移除帖子
    void removeArticle(Long articleId);

    // 倒排召回已发布帖子 ID，按「命中词数」降序.
    List<Long> searchPublishedIds(String keyword, int limit);

    // 全量重建：扫描 DB 已发布帖，同步 Redis 索引
    int rebuildAllPublished();
}
