package org.example.forumdemo.service.interfaces.ai;

import org.example.forumdemo.entity.dto.ai.AiCoverHintsRequest;
import org.example.forumdemo.entity.dto.ai.AiImageRequest;
import org.example.forumdemo.entity.dto.ai.AiWriteRequest;

import java.util.List;
import java.util.Map;

public interface AiHubService {

    Map<String, Object> write(Long userId, AiWriteRequest request);

    Map<String, Object> coverHints(Long userId, AiCoverHintsRequest request);

    Map<String, Object> image(Long userId, AiImageRequest request);

    /** 审核通过发布后写入 Redis RAG 向量索引（失败仅打日志，不抛业务异常） */
    void indexArticleRag(Map<String, Object> payload);

    /** 注册/改昵称后写入用户向量索引（失败仅打日志） */
    void indexUserRag(Map<String, Object> payload);

    /** AI 语义搜索：Redis 向量召回 + rerank；失败返回空列表 */
    List<Long> ragVectorSearchArticles(String query, List<Map<String, Object>> candidates);

    /**
     * 向量召回（qwen3-vl-embedding），返回 [{articleId, score}, ...] 按 score 降序。
     * candidates 可为空，仅走 Redis 全库向量扫描。
     */
    List<Map<String, Object>> ragArticleVectorRanked(String query, List<Map<String, Object>> candidates);

    /** 用户 AI 语义搜索：Redis 向量召回；失败返回空列表 */
    List<Long> ragVectorSearchUsers(String query, List<Map<String, Object>> candidates);
}
