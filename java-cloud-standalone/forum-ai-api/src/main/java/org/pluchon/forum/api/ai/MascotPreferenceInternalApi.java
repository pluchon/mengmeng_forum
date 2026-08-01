package org.pluchon.forum.api.ai;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 看板娘偏好内部契约（纯 API，无 @FeignClient；权威在 forum-ai）
public interface MascotPreferenceInternalApi {

    @PostMapping("/mascot/internal/{userId}/preference")
    void setMascotModel(
            @PathVariable("userId") Long userId,
            @RequestParam("mascotModelId") Long mascotModelId
    );
}
