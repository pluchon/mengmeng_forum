package org.pluchon.forum.service.interfaces.search;

import org.pluchon.forum.entity.vo.search.SearchArticleResponse;
import org.pluchon.forum.entity.vo.search.SearchUserResponse;

// 帖子搜索: 普通模式: DB 标题/正文 LIKE 模糊匹配，无结果即 empty 不走 RAG AI 模式: 向量 + RAG 语义召回 如「四川西部」≈「川西」 ，无相关结果即 empty 不强制登录: 匿名用户也能搜.
public interface SearchService {

    SearchArticleResponse searchArticles(String keyword, Integer pageNum, Integer pageSize, boolean preferAiRag, Long viewerId);

    // 用户搜索: 普通模式 DB 用户名/昵称 LIKE；AI 模式 RAG 语义匹配。均无结果则 empty.
    SearchUserResponse searchUsers(String keyword, Integer pageNum, Integer pageSize,
                                   boolean preferAiRag, Long viewerId);

    SearchArticleResponse searchCreatorArticles(Long creatorUserId, String keyword, Integer status,
                                                Integer pageNum, Integer pageSize, boolean preferAiRag);
}
