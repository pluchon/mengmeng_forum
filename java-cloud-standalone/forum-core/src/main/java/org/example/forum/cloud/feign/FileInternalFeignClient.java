package org.example.forum.cloud.feign;

import org.example.forum.api.content.FileInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "forum-content", contextId = "fileInternalFeignClient")
public interface FileInternalFeignClient extends FileInternalApi {
}
