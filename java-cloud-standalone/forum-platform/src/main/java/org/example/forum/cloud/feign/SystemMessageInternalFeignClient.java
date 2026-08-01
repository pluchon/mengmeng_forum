package org.example.forum.cloud.feign;

import org.example.forum.api.im.SystemMessageInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 过渡：消费方 Feign 客户端，契约来自 forum-im-api
@FeignClient(name = "forum-im", contextId = "systemMessageInternalFeignClient")
public interface SystemMessageInternalFeignClient extends SystemMessageInternalApi {
}
