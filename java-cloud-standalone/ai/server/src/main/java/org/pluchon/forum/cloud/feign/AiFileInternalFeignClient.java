package org.pluchon.forum.cloud.feign;

import org.pluchon.forum.api.content.FileInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

// AI 服务私有的文件域客户端
@FeignClient(name = "forum-content", contextId = "aiFileInternalFeignClient")
public interface AiFileInternalFeignClient extends FileInternalApi {
}
