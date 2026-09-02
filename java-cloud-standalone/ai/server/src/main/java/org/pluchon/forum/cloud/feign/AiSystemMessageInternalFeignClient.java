package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.im.SystemMessageInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// ai 域自有的系统消息投递客户端（牵线邀约走这条）
@FeignClient(name = "forum-im", contextId = "aiSystemMessageInternalFeignClient")
public interface AiSystemMessageInternalFeignClient extends SystemMessageInternalApi {
}
