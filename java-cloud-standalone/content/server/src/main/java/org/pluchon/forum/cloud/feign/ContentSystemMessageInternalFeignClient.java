package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.im.SystemMessageInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// content 域自有的系统消息投递客户端
@FeignClient(name = "forum-im", contextId = "contentSystemMessageInternalFeignClient")
public interface ContentSystemMessageInternalFeignClient extends SystemMessageInternalApi {
}
