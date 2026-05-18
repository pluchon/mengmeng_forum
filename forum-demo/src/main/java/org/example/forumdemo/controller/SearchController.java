package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.vo.search.SearchArticleResponse;
import org.example.forumdemo.entity.vo.search.SearchUserResponse;
import org.example.forumdemo.service.interfaces.search.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索: 帖子 / 用户；DB 优先，未命中走 Python RAG.
 * 不强制登录, 匿名用户也能搜.
 */
@Tag(name = "搜索模块")
@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private SearchService searchService;

    @Operation(summary = "搜索帖子",
            description = "DB 标题模糊匹配命中即返回(source=db); 命中 0 走 RAG 召回(source=rag); 两路均空 source=empty. " +
                    "ai=true 时跳过 DB 标题路，直接 RAG（与首页「AI 搜索」框一致）。RAG 走 Python AI 服务 rerank。")
    @GetMapping("/article")
    public Result<SearchArticleResponse> searchArticle(@RequestParam String keyword,
                                                       @RequestParam(defaultValue = "1") Integer pageNum,
                                                       @RequestParam(defaultValue = "10") Integer pageSize,
                                                       @RequestParam(required = false) String ai) {
        boolean preferAiRag = ai != null && ("1".equals(ai) || "true".equalsIgnoreCase(ai));
        return Result.success(searchService.searchArticles(keyword, pageNum, pageSize, preferAiRag));
    }

    @Operation(summary = "搜索用户",
            description = "DB 用户名/昵称 LIKE 命中即返回(source=db); 命中 0 走 RAG 召回(source=rag); 均空 source=empty. " +
                    "ai=true 时跳过 DB，直接 RAG（与帖子 AI 搜索一致）。RAG 走 Python AI 服务 rerank。")
    @GetMapping("/user")
    public Result<SearchUserResponse> searchUser(@RequestParam String keyword,
                                                 @RequestParam(defaultValue = "1") Integer pageNum,
                                                 @RequestParam(defaultValue = "10") Integer pageSize,
                                                 @RequestParam(required = false) String ai) {
        boolean preferAiRag = ai != null && ("1".equals(ai) || "true".equalsIgnoreCase(ai));
        return Result.success(searchService.searchUsers(keyword, pageNum, pageSize, preferAiRag));
    }
}
