package org.example.forumdemo.common.constant;

// 站内搜索 Redis 倒排 / 正排 Key
public final class SearchRedisKeys {

    private static final String PREFIX = "forum:search:";

    private SearchRedisKeys() {
    }

    // 倒排：检索词 → 帖子 ID 集合
    public static String inverted(String normalizedTerm) {
        return PREFIX + "inv:" + normalizedTerm;
    }

    // 正排：帖子 ID → 标题等展示字段
    public static String forward(long articleId) {
        return PREFIX + "fwd:" + articleId;
    }

    // 某帖子当前挂载的倒排词（用于更新/删除时清理）
    public static String articleTerms(long articleId) {
        return PREFIX + "article:" + articleId + ":terms";
    }

    // 已建立倒排索引的帖子 ID 登记（全量重建时清理脏数据）
    public static String indexedArticles() {
        return PREFIX + "articles";
    }
}
