package org.pluchon.forum.service.interfaces.search;

import java.util.List;

public interface ArticleSearchIndexService {

    // 按 ID 从库加载并同步索引 发布/重建用
    void syncPublishedArticle(Long articleId);

    // 从搜索索引移除帖子
    void removeArticle(Long articleId);

    // 倒排召回已发布帖子 ID，按「命中词数」降序.
    List<Long> searchPublishedIds(String keyword, int limit);

}
