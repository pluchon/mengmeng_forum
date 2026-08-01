package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.content.ArticleInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "forum-content", contextId = "articleInternalFeignClient")
public interface ArticleInternalFeignClient extends ArticleInternalApi {
}
