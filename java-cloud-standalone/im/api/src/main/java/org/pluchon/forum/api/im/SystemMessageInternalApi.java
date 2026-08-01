package org.pluchon.forum.api.im;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 系统消息内部契约（纯 API，无 @FeignClient；收口到 forum-im）
public interface SystemMessageInternalApi {

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
