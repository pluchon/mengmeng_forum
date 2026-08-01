package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.content.ArticleInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// AI 域消费 content 内部契约的客户端
@FeignClient(name = "forum-content", contextId = "articleInternalFeignClient")
public interface ArticleInternalFeignClient extends ArticleInternalApi {
}
