package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.ai.MascotPreferenceInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// 过渡：消费方 Feign，契约来自 forum-ai-api
@FeignClient(name = "forum-ai", contextId = "mascotPreferenceInternalFeignClient")
public interface MascotPreferenceInternalFeignClient extends MascotPreferenceInternalApi {
}
