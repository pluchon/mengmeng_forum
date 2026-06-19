package org.example.forumdemo.controller.user;

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
            description = "普通模式: 标题/正文 LIKE(source=db)。ai=true: 仅 Redis RAG 向量库召回(source=rag)，不走 MySQL 模糊匹配。")
    @GetMapping("/article")
    public Result<SearchArticleResponse> searchArticle(@RequestParam String keyword,
                                                       @RequestParam(defaultValue = "1") Integer pageNum,
                                                       @RequestParam(defaultValue = "10") Integer pageSize,
                                                       @RequestParam(required = false) String ai) {
        boolean preferAiRag = ("1".equals(ai) || "true".equalsIgnoreCase(ai));
        return Result.success(searchService.searchArticles(keyword, pageNum, pageSize, preferAiRag));
    }

    @Operation(summary = "搜索用户",
            description = "普通模式: 用户名/昵称 LIKE(source=db)，无结果 source=empty。ai=true: RAG 语义匹配(source=rag)，无相关结果 source=empty。")
    @GetMapping("/user")
    public Result<SearchUserResponse> searchUser(@RequestParam String keyword,
                                                 @RequestParam(defaultValue = "1") Integer pageNum,
                                                 @RequestParam(defaultValue = "10") Integer pageSize,
                                                 @RequestParam(required = false) String ai) {
        boolean preferAiRag = ai != null && ("1".equals(ai) || "true".equalsIgnoreCase(ai));
        return Result.success(searchService.searchUsers(keyword, pageNum, pageSize, preferAiRag));
    }
}
