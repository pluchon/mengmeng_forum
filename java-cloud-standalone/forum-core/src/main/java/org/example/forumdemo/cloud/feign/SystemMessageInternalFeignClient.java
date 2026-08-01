package org.example.forumdemo.cloud.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 系统消息内部创建接口（收口到 forum-im）
@FeignClient(name = "forum-im", contextId = "systemMessageInternalFeignClient")
public interface SystemMessageInternalFeignClient {

    @PostMapping("/system-message/internal/create")
    Long createMessage(
            @RequestParam("receiveUserId") Long receiveUserId,
            @RequestParam("type") Byte type,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "relatedId", required = false) Long relatedId,
            @RequestParam(value = "payload", required = false) String payload
    );
}
