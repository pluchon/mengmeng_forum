package org.example.forum.api.content;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

// 帖子域内部契约（纯 API，无 @FeignClient；供 AI 看板娘等跨域回表）
public interface ArticleInternalApi {

    @GetMapping("/article/internal/batch")
    List<ArticleInternalVO> listByIds(@RequestParam("ids") List<Long> ids);

    @GetMapping("/article/internal/search-candidates")
    List<ArticleInternalVO> searchCandidates(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "limit", defaultValue = "40") Integer limit
    );
}
