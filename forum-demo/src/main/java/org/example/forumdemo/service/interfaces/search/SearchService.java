package org.example.forumdemo.service.interfaces.search;

import org.example.forumdemo.entity.vo.search.SearchArticleResponse;
import org.example.forumdemo.entity.vo.search.SearchUserResponse;

/**
 * 帖子搜索:
 *  1) 先 DB title LIKE %kw% 做精筛(分页), 命中即返回, source=db
 *  2) DB 命中 0 -> Python AI 服务 RAG 召回, source=rag
 *  3) 两路都为空 -> source=empty
 *  preferAiRag=true（前端 AI 搜索）时跳过步骤 1，直接走 RAG。
 * 不强制登录: 匿名用户也能搜.
 */
public interface SearchService {

    SearchArticleResponse searchArticles(String keyword, Integer pageNum, Integer pageSize, boolean preferAiRag);

    /**
     * 用户搜索: 先 DB 用户名/昵称 LIKE（非 AI 模式）; 未命中或 AI 模式走 RAG.
     */
    SearchUserResponse searchUsers(String keyword, Integer pageNum, Integer pageSize, boolean preferAiRag);
}
