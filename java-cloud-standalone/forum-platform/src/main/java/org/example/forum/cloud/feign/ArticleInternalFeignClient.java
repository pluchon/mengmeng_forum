package org.example.forum.cloud.feign;

import org.example.forum.api.content.ArticleInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "forum-content", contextId = "articleInternalFeignClient")
public interface ArticleInternalFeignClient extends ArticleInternalApi {
}
